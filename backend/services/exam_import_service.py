from __future__ import annotations

import json
import logging
import threading
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any
from typing import BinaryIO

from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from backend.config.db_conf import SessionLocal
from backend.services.question_bank_import_beta import StoredImportFile
from backend.services.question_bank_import_beta import UploadedImportFile
from backend.services.question_bank_import_beta import import_question_bank_test_beta_from_stored_files_impl
from backend.services.question_bank_import_beta import normalize_structured_import_source_mode


logger = logging.getLogger(__name__)

IMPORT_JOB_STATUS_PENDING = "pending"
IMPORT_JOB_STATUS_RUNNING = "running"
IMPORT_JOB_STATUS_COMPLETED = "completed"
IMPORT_JOB_STATUS_FAILED = "failed"
IMPORT_JOB_ACTIVE_STATUSES = {IMPORT_JOB_STATUS_PENDING, IMPORT_JOB_STATUS_RUNNING}

_active_job_ids: set[int] = set()
_active_job_ids_lock = threading.Lock()
_import_job_run_semaphore = threading.Semaphore(1)
IMPORT_WRITE_CHUNK_SIZE = 1024 * 1024


@dataclass(slots=True)
class QueuedImportFile:
    filename: str
    file_obj: BinaryIO


def utcnow() -> datetime:
    return datetime.utcnow()


def get_project_root() -> Path:
    return Path(__file__).resolve().parents[2]


def get_import_job_root() -> Path:
    root = get_project_root() / "storage" / "import-jobs"
    root.mkdir(parents=True, exist_ok=True)
    return root


def get_import_job_dir(storage_path: str) -> Path:
    return get_import_job_root() / storage_path


def load_exam_import_job_model():
    from backend.models.exam_import_job_models import ExamImportJob

    return ExamImportJob


def normalize_bank_name(value: str | None) -> str | None:
    text = str(value or "").strip()
    return text or None


def copy_binary_stream_in_chunks(
    source: BinaryIO,
    destination: Path,
    *,
    chunk_size: int = IMPORT_WRITE_CHUNK_SIZE,
) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("wb") as stream:
        while True:
            chunk = source.read(chunk_size)
            if not chunk:
                break
            stream.write(chunk)


def build_missing_table_error() -> ValueError:
    return ValueError("缺少 exam_import_job 表，请先执行建表 SQL")


def serialize_import_job(job: Any) -> dict[str, Any]:
    result_payload = job.result_json if isinstance(job.result_json, dict) else {}
    return {
        "job_id": job.exam_import_job_id,
        "job_name": job.job_name,
        "bank_name": job.bank_name,
        "source_mode": job.source_mode,
        "status": job.status,
        "uploaded_file_count": int(job.uploaded_file_count or 0),
        "resolved_file_count": int(job.resolved_file_count or 0),
        "manifest_count": int(job.manifest_count or 0),
        "success_count": int(job.success_count or 0),
        "skipped_count": int(result_payload.get("skipped_count") or 0),
        "failure_count": int(job.failure_count or 0),
        "imported_bank_count": int(job.imported_bank_count or 0),
        "imported_paper_count": int(job.imported_paper_count or 0),
        "imported_section_count": int(job.imported_section_count or 0),
        "imported_group_count": int(job.imported_group_count or 0),
        "imported_question_count": int(job.imported_question_count or 0),
        "imported_answer_count": int(job.imported_answer_count or 0),
        "imported_blank_count": int(job.imported_blank_count or 0),
        "imported_option_count": int(job.imported_option_count or 0),
        "imported_asset_count": int(job.imported_asset_count or 0),
        "progress_message": job.progress_message,
        "error_message": job.error_message,
        "start_time": job.start_time,
        "finish_time": job.finish_time,
        "create_time": job.create_time,
        "update_time": job.update_time,
        "items": result_payload.get("items", []),
        "failures": result_payload.get("failures", []),
    }


def build_import_job_metadata(
    *,
    source_mode: str,
    bank_name: str | None,
    entry_paths_json: str | None,
    uploaded_files: list[QueuedImportFile],
) -> dict[str, Any]:
    return {
        "source_mode": source_mode,
        "bank_name": bank_name,
        "entry_paths_json": entry_paths_json,
        "files": [
            {
                "filename": item.filename,
                "stored_name": f"file_{index:05d}.bin",
            }
            for index, item in enumerate(uploaded_files, start=1)
        ],
    }


def persist_import_job_files(
    *,
    job_storage_path: str,
    metadata: dict[str, Any],
    uploaded_files: list[QueuedImportFile],
) -> None:
    job_dir = get_import_job_dir(job_storage_path)
    job_dir.mkdir(parents=True, exist_ok=True)

    for item, file_meta in zip(uploaded_files, metadata["files"], strict=True):
        destination = job_dir / file_meta["stored_name"]
        if hasattr(item.file_obj, "seek"):
            item.file_obj.seek(0)
        copy_binary_stream_in_chunks(item.file_obj, destination)

    metadata_path = job_dir / "metadata.json"
    metadata_path.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def load_import_job_metadata(storage_path: str) -> tuple[dict[str, Any], Path]:
    job_dir = get_import_job_dir(storage_path)
    metadata_path = job_dir / "metadata.json"
    if not metadata_path.exists():
        raise ValueError("导入任务缺少 metadata.json")
    return json.loads(metadata_path.read_text(encoding="utf-8")), job_dir


def load_import_job_uploaded_files(storage_path: str) -> tuple[dict[str, Any], list[UploadedImportFile]]:
    metadata, job_dir = load_import_job_metadata(storage_path)
    uploaded_files: list[UploadedImportFile] = []
    for item in metadata.get("files") or []:
        stored_name = str(item.get("stored_name") or "").strip()
        filename = str(item.get("filename") or "").strip() or stored_name
        if not stored_name:
            continue
        file_path = job_dir / stored_name
        if not file_path.exists():
            raise ValueError(f"导入任务缺少文件：{stored_name}")
        uploaded_files.append(
            UploadedImportFile(
                filename=filename,
                raw_bytes=file_path.read_bytes(),
            )
        )
    return metadata, uploaded_files


def load_import_job_stored_files(storage_path: str) -> tuple[dict[str, Any], list[StoredImportFile]]:
    metadata, job_dir = load_import_job_metadata(storage_path)
    stored_files: list[StoredImportFile] = []
    for item in metadata.get("files") or []:
        stored_name = str(item.get("stored_name") or "").strip()
        filename = str(item.get("filename") or "").strip() or stored_name
        if not stored_name:
            continue
        file_path = job_dir / stored_name
        if not file_path.exists():
            raise ValueError(f"导入任务缺少文件：{stored_name}")
        stored_files.append(
            StoredImportFile(
                filename=filename,
                file_path=file_path,
            )
        )
    return metadata, stored_files


def import_question_bank(
    db: Session,
    *,
    source_mode: str,
    uploaded_files: list[QueuedImportFile],
    entry_paths_json: str | None,
    bank_name: str | None,
) -> dict[str, Any]:
    return create_import_job(
        db,
        source_mode=source_mode,
        uploaded_files=uploaded_files,
        entry_paths_json=entry_paths_json,
        bank_name=bank_name,
    )


def create_import_job(
    db: Session,
    *,
    source_mode: str,
    uploaded_files: list[QueuedImportFile],
    entry_paths_json: str | None,
    bank_name: str | None,
) -> dict[str, Any]:
    normalized_source_mode = normalize_structured_import_source_mode(source_mode)
    if not uploaded_files:
        raise ValueError("请至少上传一个文件")

    normalized_bank_name = normalize_bank_name(bank_name)
    job_name = normalized_bank_name or f"题库导入 {utcnow().strftime('%Y-%m-%d %H:%M:%S')}"

    ExamImportJob = load_exam_import_job_model()
    job = ExamImportJob(
        job_name=job_name,
        bank_name=normalized_bank_name,
        source_mode=normalized_source_mode,
        status=IMPORT_JOB_STATUS_PENDING,
        uploaded_file_count=len(uploaded_files),
        progress_message="文件上传完成，等待导入",
        delete_flag="1",
    )

    try:
        db.add(job)
        db.flush()
        job.storage_path = f"job_{job.exam_import_job_id}"
        metadata = build_import_job_metadata(
            source_mode=normalized_source_mode,
            bank_name=normalized_bank_name,
            entry_paths_json=entry_paths_json,
            uploaded_files=uploaded_files,
        )
        persist_import_job_files(
            job_storage_path=job.storage_path,
            metadata=metadata,
            uploaded_files=uploaded_files,
        )
        db.commit()
        db.refresh(job)
    except SQLAlchemyError as exc:
        db.rollback()
        logger.exception("Create import job failed")
        raise build_missing_table_error() from exc
    except Exception:
        db.rollback()
        raise

    start_import_job(job.exam_import_job_id)
    return serialize_import_job(job)


def get_import_job_detail(db: Session, *, job_id: int) -> dict[str, Any]:
    ExamImportJob = load_exam_import_job_model()
    try:
        job = (
            db.query(ExamImportJob)
            .filter(ExamImportJob.exam_import_job_id == job_id)
            .filter(ExamImportJob.delete_flag == "1")
            .first()
        )
    except SQLAlchemyError as exc:
        raise build_missing_table_error() from exc

    if not job:
        raise ValueError("导入任务不存在")

    return serialize_import_job(job)


def mark_import_job_failed(db: Session, *, job_id: int, error_message: str) -> None:
    ExamImportJob = load_exam_import_job_model()
    job = (
        db.query(ExamImportJob)
        .filter(ExamImportJob.exam_import_job_id == job_id)
        .filter(ExamImportJob.delete_flag == "1")
        .first()
    )
    if not job:
        return
    job.status = IMPORT_JOB_STATUS_FAILED
    job.error_message = error_message
    job.progress_message = "导入失败"
    job.finish_time = utcnow()
    db.commit()


def update_import_job_progress(
    db: Session,
    *,
    job_id: int,
    progress_payload: dict[str, Any],
) -> None:
    ExamImportJob = load_exam_import_job_model()
    job = (
        db.query(ExamImportJob)
        .filter(ExamImportJob.exam_import_job_id == job_id)
        .filter(ExamImportJob.delete_flag == "1")
        .first()
    )
    if not job:
        return

    job.status = IMPORT_JOB_STATUS_RUNNING
    job.resolved_file_count = int(progress_payload.get("resolved_file_count") or 0)
    job.manifest_count = int(progress_payload.get("manifest_count") or 0)
    job.success_count = int(progress_payload.get("success_count") or 0)
    job.failure_count = int(progress_payload.get("failure_count") or 0)
    job.imported_bank_count = int(progress_payload.get("imported_bank_count") or 0)
    job.imported_paper_count = int(progress_payload.get("imported_paper_count") or 0)
    job.imported_section_count = int(progress_payload.get("imported_section_count") or 0)
    job.imported_group_count = int(progress_payload.get("imported_group_count") or 0)
    job.imported_question_count = int(progress_payload.get("imported_question_count") or 0)
    job.imported_answer_count = int(progress_payload.get("imported_answer_count") or 0)
    job.imported_blank_count = int(progress_payload.get("imported_blank_count") or 0)
    job.imported_option_count = int(progress_payload.get("imported_option_count") or 0)
    job.imported_asset_count = int(progress_payload.get("imported_asset_count") or 0)
    job.progress_message = progress_payload.get("progress_message") or job.progress_message
    job.result_json = {
        "skipped_count": int(progress_payload.get("skipped_count") or 0),
        "items": progress_payload.get("items") or [],
        "failures": progress_payload.get("failures") or [],
    }
    job.error_message = None
    db.commit()


def start_import_job(job_id: int) -> None:
    with _active_job_ids_lock:
        if job_id in _active_job_ids:
            return
        _active_job_ids.add(job_id)

    worker = threading.Thread(
        target=process_import_job,
        args=(job_id,),
        daemon=True,
        name=f"exam-import-job-{job_id}",
    )
    worker.start()


def finalize_import_job(job: Any, result: dict[str, Any]) -> None:
    job.status = IMPORT_JOB_STATUS_COMPLETED
    job.resolved_file_count = int(result.get("resolved_file_count") or 0)
    job.manifest_count = int(result.get("manifest_count") or 0)
    job.success_count = int(result.get("success_count") or 0)
    job.failure_count = int(result.get("failure_count") or 0)
    job.imported_bank_count = int(result.get("imported_bank_count") or 0)
    job.imported_paper_count = int(result.get("imported_paper_count") or 0)
    job.imported_section_count = int(result.get("imported_section_count") or 0)
    job.imported_group_count = int(result.get("imported_group_count") or 0)
    job.imported_question_count = int(result.get("imported_question_count") or 0)
    job.imported_answer_count = int(result.get("imported_answer_count") or 0)
    job.imported_blank_count = int(result.get("imported_blank_count") or 0)
    job.imported_option_count = int(result.get("imported_option_count") or 0)
    job.imported_asset_count = int(result.get("imported_asset_count") or 0)
    job.result_json = {
        "items": result.get("items") or [],
        "failures": result.get("failures") or [],
    }
    job.error_message = None
    job.progress_message = (
        f"导入完成：成功 {job.success_count} 个题包，失败 {job.failure_count} 个"
    )
    job.finish_time = utcnow()


def process_import_job(job_id: int) -> None:
    _import_job_run_semaphore.acquire()
    db = SessionLocal()
    try:
        ExamImportJob = load_exam_import_job_model()
        job = (
            db.query(ExamImportJob)
            .filter(ExamImportJob.exam_import_job_id == job_id)
            .filter(ExamImportJob.delete_flag == "1")
            .first()
        )
        if not job or job.status not in IMPORT_JOB_ACTIVE_STATUSES:
            return

        job.status = IMPORT_JOB_STATUS_RUNNING
        job.start_time = job.start_time or utcnow()
        job.progress_message = "正在解析并导入题库"
        job.error_message = None
        db.commit()

        metadata, uploaded_files = load_import_job_uploaded_files(job.storage_path)
        result = import_question_bank_test_beta_impl(
            db,
            source_mode=metadata.get("source_mode") or job.source_mode,
            uploaded_files=uploaded_files,
            entry_paths_json=metadata.get("entry_paths_json"),
            bank_name_override=metadata.get("bank_name"),
            progress_callback=lambda payload: update_import_job_progress(
                db,
                job_id=job_id,
                progress_payload=payload,
            ),
        )

        job = (
            db.query(ExamImportJob)
            .filter(ExamImportJob.exam_import_job_id == job_id)
            .filter(ExamImportJob.delete_flag == "1")
            .first()
        )
        if not job:
            return

        finalize_import_job(job, result)
        db.commit()
    except Exception as exc:  # noqa: BLE001
        logger.exception("Import job %s failed", job_id)
        db.rollback()
        try:
            mark_import_job_failed(
                db,
                job_id=job_id,
                error_message=str(exc),
            )
        except Exception:  # noqa: BLE001
            db.rollback()
            logger.exception("Failed to update import job %s failure status", job_id)
    finally:
        db.close()
        _import_job_run_semaphore.release()
        with _active_job_ids_lock:
            _active_job_ids.discard(job_id)


def resume_pending_import_jobs() -> None:
    db = SessionLocal()
    try:
        ExamImportJob = load_exam_import_job_model()
        rows = (
            db.query(ExamImportJob.exam_import_job_id)
            .filter(ExamImportJob.delete_flag == "1")
            .filter(ExamImportJob.status.in_(tuple(IMPORT_JOB_ACTIVE_STATUSES)))
            .all()
        )
    except Exception as exc:  # noqa: BLE001
        db.rollback()
        logger.warning("Skip resuming import jobs: %s", exc)
        return
    finally:
        db.close()

    for row in rows:
        start_import_job(int(row.exam_import_job_id))


def finalize_import_job(job: Any, result: dict[str, Any]) -> None:
    job.status = IMPORT_JOB_STATUS_COMPLETED
    job.resolved_file_count = int(result.get("resolved_file_count") or 0)
    job.manifest_count = int(result.get("manifest_count") or 0)
    job.success_count = int(result.get("success_count") or 0)
    job.failure_count = int(result.get("failure_count") or 0)
    job.imported_bank_count = int(result.get("imported_bank_count") or 0)
    job.imported_paper_count = int(result.get("imported_paper_count") or 0)
    job.imported_section_count = int(result.get("imported_section_count") or 0)
    job.imported_group_count = int(result.get("imported_group_count") or 0)
    job.imported_question_count = int(result.get("imported_question_count") or 0)
    job.imported_answer_count = int(result.get("imported_answer_count") or 0)
    job.imported_blank_count = int(result.get("imported_blank_count") or 0)
    job.imported_option_count = int(result.get("imported_option_count") or 0)
    job.imported_asset_count = int(result.get("imported_asset_count") or 0)
    job.result_json = {
        "skipped_count": int(result.get("skipped_count") or 0),
        "items": result.get("items") or [],
        "failures": result.get("failures") or [],
    }
    job.error_message = None
    skipped_count = int(result.get("skipped_count") or 0)
    job.progress_message = f"导入完成：成功 {job.success_count} 个题包，跳过 {skipped_count} 个，失败 {job.failure_count} 个"
    job.finish_time = utcnow()


def process_import_job(job_id: int) -> None:
    _import_job_run_semaphore.acquire()
    db = SessionLocal()
    try:
        ExamImportJob = load_exam_import_job_model()
        job = (
            db.query(ExamImportJob)
            .filter(ExamImportJob.exam_import_job_id == job_id)
            .filter(ExamImportJob.delete_flag == "1")
            .first()
        )
        if not job or job.status not in IMPORT_JOB_ACTIVE_STATUSES:
            return

        job.status = IMPORT_JOB_STATUS_RUNNING
        job.start_time = job.start_time or utcnow()
        job.progress_message = "正在解析并导入题库"
        job.error_message = None
        db.commit()

        metadata, stored_files = load_import_job_stored_files(job.storage_path)
        result = import_question_bank_test_beta_from_stored_files_impl(
            db,
            source_mode=metadata.get("source_mode") or job.source_mode,
            stored_files=stored_files,
            entry_paths_json=metadata.get("entry_paths_json"),
            bank_name_override=metadata.get("bank_name"),
            progress_callback=lambda payload: update_import_job_progress(
                db,
                job_id=job_id,
                progress_payload=payload,
            ),
        )

        job = (
            db.query(ExamImportJob)
            .filter(ExamImportJob.exam_import_job_id == job_id)
            .filter(ExamImportJob.delete_flag == "1")
            .first()
        )
        if not job:
            return

        finalize_import_job(job, result)
        db.commit()
    except Exception as exc:  # noqa: BLE001
        logger.exception("Import job %s failed", job_id)
        db.rollback()
        try:
            mark_import_job_failed(
                db,
                job_id=job_id,
                error_message=str(exc),
            )
        except Exception:  # noqa: BLE001
            db.rollback()
            logger.exception("Failed to update import job %s failure status", job_id)
    finally:
        db.close()
        _import_job_run_semaphore.release()
        with _active_job_ids_lock:
            _active_job_ids.discard(job_id)
