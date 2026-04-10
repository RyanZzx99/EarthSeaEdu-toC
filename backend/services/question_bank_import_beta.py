from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from dataclasses import dataclass
from html import unescape
from html.parser import HTMLParser
from io import BytesIO
from pathlib import PurePosixPath
from typing import Any
from zipfile import BadZipFile
from zipfile import ZipFile

from sqlalchemy.orm import Session

from backend.models.ielts_exam_models import ExamAsset
from backend.models.ielts_exam_models import ExamBank
from backend.models.ielts_exam_models import ExamGroup
from backend.models.ielts_exam_models import ExamGroupOption
from backend.models.ielts_exam_models import ExamPaper
from backend.models.ielts_exam_models import ExamQuestion
from backend.models.ielts_exam_models import ExamQuestionAnswer
from backend.models.ielts_exam_models import ExamQuestionBlank
from backend.models.ielts_exam_models import ExamSection

STRUCTURED_IMPORT_SOURCE_MODES = {"zip", "directory", "files"}
TEXT_BREAK_TAGS = {
    "p",
    "div",
    "br",
    "li",
    "tr",
    "td",
    "th",
    "table",
    "thead",
    "tbody",
    "ul",
    "ol",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
}
OPTION_KEY_PATTERN = re.compile(r"^\s*([A-Z])[\.\)\-:：]?\s+(.*)$", re.S)
QUESTION_NUMBER_PATTERN = re.compile(r"(\d+)(?!.*\d)")
IMAGE_SOURCE_PATTERN = re.compile(r"<img[^>]+src=['\"]([^'\"]+)['\"]", re.I)
OPTION_HTML_BLOCK_PATTERN = re.compile(r"(<(?P<tag>p|li)\b[^>]*>.*?</(?P=tag)>)", re.I | re.S)
OPTION_SIMPLE_TABLE_CELL_PATTERN = re.compile(r"<td\b[^>]*>\s*(?P<content>[^<]*?)\s*</td>", re.I | re.S)
OPTION_PREFIX_PATTERN = re.compile(
    r"^\s*(?P<key>(?:[A-Z]|[ivxlcdm]+))[\.\)\-:：]?\s+(?P<content>.+?)\s*$",
    re.I | re.S,
)
COMPACT_TABLE_OPTION_PATTERN = re.compile(
    r"^\s*(?P<key>[A-Z])(?P<content>[a-z].+?)\s*$",
    re.S,
)
BOOK_TEST_PATTERNS = (
    re.compile(r"真题\s*0*(\d{1,2})\s*[-－]\s*0*(\d+)", re.I),
    re.compile(r"0*(\d{1,2})\s*[-－]\s*0*(\d+)", re.I),
    re.compile(r"真题\s*0*(\d{1,2}).*?test\s*0*(\d+)", re.I),
    re.compile(r"test\s*0*(\d+)", re.I),
)
SPECIAL_ANSWER_TOKENS = {"TRUE", "FALSE", "NOT GIVEN", "YES", "NO"}


@dataclass(slots=True)
class UploadedImportFile:
    filename: str
    raw_bytes: bytes


@dataclass(slots=True)
class VirtualImportFile:
    logical_path: str
    raw_bytes: bytes
    origin_name: str


@dataclass(slots=True)
class ImportPackage:
    root_path: str
    manifest_path: str
    files: dict[str, VirtualImportFile]


@dataclass(slots=True)
class PackageImportResult:
    bank_code: str
    bank_name: str
    paper_code: str
    paper_name: str
    subject_type: str
    import_status: str
    counts: dict[str, int]


class HtmlTextExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.parts: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in TEXT_BREAK_TAGS:
            self.parts.append("\n")
        if tag == "img":
            attrs_map = dict(attrs)
            alt_text = (attrs_map.get("alt") or "").strip()
            if alt_text:
                self.parts.append(alt_text)
                self.parts.append("\n")

    def handle_endtag(self, tag: str) -> None:
        if tag in TEXT_BREAK_TAGS:
            self.parts.append("\n")

    def handle_data(self, data: str) -> None:
        if data:
            self.parts.append(data)

    def get_text(self) -> str:
        text = unescape("".join(self.parts))
        text = text.replace("\r\n", "\n").replace("\r", "\n")
        text = re.sub(r"[ \t\f\v]+", " ", text)
        text = re.sub(r"\n{3,}", "\n\n", text)
        lines = [line.strip() for line in text.split("\n")]
        return "\n".join(line for line in lines if line).strip()


def import_question_bank_test_beta_impl(
    db: Session,
    *,
    source_mode: str,
    uploaded_files: list[UploadedImportFile],
    entry_paths_json: str | None,
) -> dict[str, Any]:
    normalized_source_mode = normalize_structured_import_source_mode(source_mode)
    normalized_uploaded_files = normalize_uploaded_import_files(uploaded_files)
    if not normalized_uploaded_files:
        raise ValueError("请至少上传一个文件")

    entry_paths = parse_entry_paths_json(entry_paths_json, expected_count=len(normalized_uploaded_files))
    virtual_files = build_virtual_import_files(
        uploaded_files=normalized_uploaded_files,
        entry_paths=entry_paths,
    )
    packages = discover_import_packages(virtual_files)
    if not packages:
        raise ValueError("导入内容中没有找到 manifest.json")

    success_items: list[dict[str, Any]] = []
    failed_items: list[dict[str, str]] = []
    touched_bank_codes: set[str] = set()
    aggregate_counts = {
        "sections": 0,
        "groups": 0,
        "questions": 0,
        "answers": 0,
        "blanks": 0,
        "options": 0,
        "assets": 0,
    }

    for package in packages:
        try:
            result = import_single_ielts_package(db, package=package)
            db.commit()
            touched_bank_codes.add(result.bank_code)
            success_items.append(
                {
                    "bank_code": result.bank_code,
                    "bank_name": result.bank_name,
                    "paper_code": result.paper_code,
                    "paper_name": result.paper_name,
                    "subject_type": result.subject_type,
                    "import_status": result.import_status,
                    "package_root": package.root_path or ".",
                }
            )
            for key, value in result.counts.items():
                aggregate_counts[key] += value
        except Exception as exc:  # noqa: BLE001
            db.rollback()
            failed_items.append(
                {
                    "package_root": package.root_path or ".",
                    "message": str(exc),
                }
            )

    if not success_items:
        message = "；".join(f"{item['package_root']}：{item['message']}" for item in failed_items[:5])
        raise ValueError(message or "未成功导入任何题库")

    return {
        "source_mode": normalized_source_mode,
        "uploaded_file_count": len(normalized_uploaded_files),
        "resolved_file_count": len(virtual_files),
        "manifest_count": len(packages),
        "success_count": len(success_items),
        "failure_count": len(failed_items),
        "imported_bank_count": len(touched_bank_codes),
        "imported_paper_count": len(success_items),
        "imported_section_count": aggregate_counts["sections"],
        "imported_group_count": aggregate_counts["groups"],
        "imported_question_count": aggregate_counts["questions"],
        "imported_answer_count": aggregate_counts["answers"],
        "imported_blank_count": aggregate_counts["blanks"],
        "imported_option_count": aggregate_counts["options"],
        "imported_asset_count": aggregate_counts["assets"],
        "items": success_items,
        "failures": failed_items,
    }


def normalize_uploaded_import_files(uploaded_files: list[UploadedImportFile]) -> list[UploadedImportFile]:
    result: list[UploadedImportFile] = []
    for index, item in enumerate(uploaded_files, start=1):
        filename = (item.filename or "").strip() or f"file_{index}"
        if item.raw_bytes is None:
            raise ValueError(f"{filename} 文件内容为空")
        result.append(UploadedImportFile(filename=filename, raw_bytes=item.raw_bytes))
    return result


def normalize_structured_import_source_mode(value: str | None) -> str:
    normalized = (value or "").strip().lower()
    if normalized not in STRUCTURED_IMPORT_SOURCE_MODES:
        raise ValueError("导入模式不合法，仅支持 zip / directory / files")
    return normalized


def parse_entry_paths_json(entry_paths_json: str | None, *, expected_count: int) -> list[str]:
    if not entry_paths_json:
        return []
    try:
        raw_value = json.loads(entry_paths_json)
    except json.JSONDecodeError as exc:
        raise ValueError("entry_paths_json 不是合法 JSON") from exc
    if not isinstance(raw_value, list):
        raise ValueError("entry_paths_json 必须是数组")
    normalized_paths = [str(item or "").strip() for item in raw_value]
    if normalized_paths and len(normalized_paths) != expected_count:
        raise ValueError("entry_paths_json 与上传文件数量不一致")
    return normalized_paths


def build_virtual_import_files(
    *,
    uploaded_files: list[UploadedImportFile],
    entry_paths: list[str],
) -> dict[str, VirtualImportFile]:
    virtual_files: dict[str, VirtualImportFile] = {}
    for index, uploaded_file in enumerate(uploaded_files):
        filename = uploaded_file.filename
        logical_path = normalize_import_path(entry_paths[index] if index < len(entry_paths) else filename)
        logical_path = logical_path or normalize_import_path(filename)
        if PurePosixPath(filename).suffix.lower() == ".zip":
            archive_prefix = shorten_identifier(slugify_ascii(PurePosixPath(filename).stem) or f"archive_{index + 1}", 60)
            try:
                with ZipFile(BytesIO(uploaded_file.raw_bytes)) as zip_file:
                    for zip_info in zip_file.infolist():
                        if zip_info.is_dir():
                            continue
                        nested_path = normalize_import_path(f"{archive_prefix}/{zip_info.filename}")
                        if not nested_path:
                            continue
                        add_virtual_import_file(
                            virtual_files,
                            logical_path=nested_path,
                            raw_bytes=zip_file.read(zip_info.filename),
                            origin_name=filename,
                        )
            except BadZipFile as exc:
                raise ValueError(f"{filename} 不是合法 zip 压缩包") from exc
            continue

        if not logical_path:
            raise ValueError(f"{filename} 的导入路径为空")
        add_virtual_import_file(
            virtual_files,
            logical_path=logical_path,
            raw_bytes=uploaded_file.raw_bytes,
            origin_name=filename,
        )
    return virtual_files


def add_virtual_import_file(
    virtual_files: dict[str, VirtualImportFile],
    *,
    logical_path: str,
    raw_bytes: bytes,
    origin_name: str,
) -> None:
    if logical_path in virtual_files:
        raise ValueError(
            f"导入路径冲突：{logical_path}。如果是多套题库原始文件，请优先使用压缩包或文件夹导入。"
        )
    virtual_files[logical_path] = VirtualImportFile(
        logical_path=logical_path,
        raw_bytes=raw_bytes,
        origin_name=origin_name,
    )


def discover_import_packages(virtual_files: dict[str, VirtualImportFile]) -> list[ImportPackage]:
    manifest_paths = sorted(
        path
        for path in virtual_files
        if PurePosixPath(path).name.lower() == "manifest.json"
    )

    packages: list[ImportPackage] = []
    for manifest_path in manifest_paths:
        root_path = str(PurePosixPath(manifest_path).parent)
        if root_path == ".":
            root_path = ""

        package_files: dict[str, VirtualImportFile] = {}
        for full_path, item in virtual_files.items():
            relative_path = make_relative_path(full_path, root_path)
            if relative_path is not None:
                package_files[relative_path] = item

        packages.append(
            ImportPackage(
                root_path=root_path,
                manifest_path=manifest_path,
                files=package_files,
            )
        )
    return packages


def make_relative_path(full_path: str, root_path: str) -> str | None:
    normalized_full_path = normalize_import_path(full_path)
    normalized_root_path = normalize_import_path(root_path)
    if not normalized_root_path:
        return normalized_full_path
    if normalized_full_path == normalized_root_path:
        return None
    prefix = f"{normalized_root_path}/"
    if not normalized_full_path.startswith(prefix):
        return None
    return normalized_full_path[len(prefix) :]


def import_single_ielts_package(db: Session, *, package: ImportPackage) -> PackageImportResult:
    manifest = read_json_from_package(package, "manifest.json")
    manifest_passages = manifest.get("passages")
    if not isinstance(manifest_passages, list) or not manifest_passages:
        raise ValueError("manifest.json 缺少 passages[]")

    subject_type = infer_subject_type(manifest=manifest, package=package)
    book_code, test_no = extract_book_and_test_numbers(manifest=manifest, package=package)
    bank_code, bank_name = build_bank_identity(manifest=manifest, package=package, book_code=book_code)
    paper_code, paper_name = build_paper_identity(
        manifest=manifest,
        package=package,
        subject_type=subject_type,
        book_code=book_code,
        test_no=test_no,
        bank_code=bank_code,
    )

    bank = get_or_create_exam_bank(
        db,
        bank_code=bank_code,
        bank_name=bank_name,
        subject_type=subject_type,
        source_name=package.root_path or manifest.get("paper") or manifest.get("module") or "import-beta",
    )

    import_status = "imported"
    existing_paper = db.query(ExamPaper).filter(ExamPaper.paper_code == paper_code).first()
    if existing_paper is not None:
        import_status = "replaced"
        db.delete(existing_paper)
        db.flush()

    paper = ExamPaper(
        exam_bank_id=bank.exam_bank_id,
        paper_code=paper_code,
        paper_name=paper_name,
        module_name=normalize_string(manifest.get("module")),
        subject_type=subject_type,
        book_code=book_code,
        test_no=test_no,
        status=1,
        delete_flag="1",
    )
    db.add(paper)
    db.flush()

    counts = {
        "sections": 0,
        "groups": 0,
        "questions": 0,
        "answers": 0,
        "blanks": 0,
        "options": 0,
        "assets": 0,
    }

    for section_index, manifest_passage in enumerate(manifest_passages, start=1):
        if not isinstance(manifest_passage, dict):
            continue

        source_file = normalize_string(manifest_passage.get("file"))
        if not source_file:
            raise ValueError(f"{package.root_path or '.'} 的 manifest.json 存在缺少 file 的 section")

        section_doc = read_json_from_package(package, source_file)
        section_payload = resolve_section_payload(
            section_doc=section_doc,
            expected_section_id=normalize_string(manifest_passage.get("id")),
        )

        section = ExamSection(
            exam_paper_id=paper.exam_paper_id,
            section_id=normalize_string(section_payload.get("id")) or normalize_string(manifest_passage.get("id")),
            section_no=parse_section_no(section_payload.get("id")) or section_index,
            section_title=normalize_string(section_payload.get("title")) or normalize_string(manifest_passage.get("title")),
            content_html=normalize_string(section_payload.get("content")),
            content_text=html_to_text(section_payload.get("content")),
            instructions_html=normalize_string(section_payload.get("instructions")),
            instructions_text=html_to_text(section_payload.get("instructions")),
            sort_order=section_index,
            source_file=source_file,
            status=1,
            delete_flag="1",
        )
        db.add(section)
        db.flush()
        counts["sections"] += 1

        section_audio_assets = create_assets_for_owner(
            db,
            paper=paper,
            owner_type="section",
            source_paths=[normalize_string(section_payload.get("audio"))],
            section=section,
            asset_role="primary_audio",
        )
        if section_audio_assets:
            section.primary_audio_asset_id = section_audio_assets[0].exam_asset_id
            counts["assets"] += len(section_audio_assets)

        section_image_assets = create_assets_for_owner(
            db,
            paper=paper,
            owner_type="section",
            source_paths=extract_image_sources(section.instructions_html) + extract_image_sources(section.content_html),
            section=section,
            asset_role="primary_image",
        )
        if section_image_assets:
            section.primary_image_asset_id = section_image_assets[0].exam_asset_id
            counts["assets"] += len(section_image_assets)

        groups = section_payload.get("groups")
        if not isinstance(groups, list):
            groups = []

        for group_index, group_payload in enumerate(groups, start=1):
            if not isinstance(group_payload, dict):
                continue

            group_content_html = extract_group_content_html(group_payload)
            raw_type = normalize_string(group_payload.get("type"))
            stat_type = infer_stat_type(raw_type=raw_type, group_payload=group_payload)
            blank_ids = collect_group_blank_ids(group_payload)
            raw_question_ids = collect_group_question_ids(group_payload)
            shared_options = extract_group_shared_options(group_payload)
            group = ExamGroup(
                exam_section_id=section.exam_section_id,
                group_id=normalize_string(group_payload.get("id")),
                group_title=normalize_string(group_payload.get("title")),
                raw_type=raw_type,
                stat_type=stat_type,
                instructions_html=normalize_string(group_payload.get("instructions")),
                instructions_text=html_to_text(group_payload.get("instructions")),
                content_html=group_content_html,
                content_text=html_to_text(group_content_html),
                has_shared_options=1 if shared_options else 0,
                has_blanks=1 if blank_ids else 0,
                primary_image_asset_id=None,
                structure_json={
                    "blank_ids": blank_ids,
                    "raw_question_ids": raw_question_ids,
                },
                sort_order=group_index,
                status=1,
                delete_flag="1",
            )
            db.add(group)
            db.flush()
            counts["groups"] += 1

            group_image_assets = create_assets_for_owner(
                db,
                paper=paper,
                owner_type="group",
                source_paths=extract_image_sources(group.instructions_html) + extract_image_sources(group.content_html),
                section=section,
                group=group,
                asset_role="primary_image",
            )
            if group_image_assets:
                group.primary_image_asset_id = group_image_assets[0].exam_asset_id
                counts["assets"] += len(group_image_assets)

            for option_index, raw_option in enumerate(shared_options, start=1):
                option_html = normalize_string(raw_option)
                option_key = extract_option_key(option_html, fallback_index=option_index)
                option_text = strip_option_prefix(html_to_text(option_html), option_key=option_key)
                db.add(
                    ExamGroupOption(
                        exam_group_id=group.exam_group_id,
                        option_key=option_key,
                        option_html=option_html,
                        option_text=option_text,
                        sort_order=option_index,
                        status=1,
                        delete_flag="1",
                    )
                )
                counts["options"] += 1

            import_questions_for_group(
                db,
                paper=paper,
                section=section,
                group=group,
                group_payload=group_payload,
                counts=counts,
            )

    return PackageImportResult(
        bank_code=bank.bank_code,
        bank_name=bank.bank_name,
        paper_code=paper.paper_code,
        paper_name=paper.paper_name,
        subject_type=paper.subject_type,
        import_status=import_status,
        counts=counts,
    )


def import_questions_for_group(
    db: Session,
    *,
    paper: ExamPaper,
    section: ExamSection,
    group: ExamGroup,
    group_payload: dict[str, Any],
    counts: dict[str, int],
) -> None:
    questions = group_payload.get("questions")
    if not isinstance(questions, list):
        return

    question_sort_order = 0
    for source_question in questions:
        if not isinstance(source_question, dict):
            continue

        blanks = source_question.get("blanks")
        if isinstance(blanks, list) and blanks:
            question_sort_order = import_blank_questions(
                db,
                paper=paper,
                section=section,
                group=group,
                source_question=source_question,
                blanks=blanks,
                counts=counts,
                question_sort_order=question_sort_order,
            )
            continue

        question_sort_order += 1
        question_id = shorten_identifier(
            normalize_string(source_question.get("id")) or f"{group.group_id or 'group'}-q{question_sort_order}",
            100,
        )
        question_no = (
            parse_question_no(question_id)
            or parse_question_no(source_question.get("stem"))
            or parse_question_no(source_question.get("content"))
            or question_sort_order
        )
        question_code = build_question_code(
            paper_code=paper.paper_code,
            section_id=section.section_id,
            question_no=question_no,
            question_id=question_id,
            blank_id=None,
        )
        question = ExamQuestion(
            exam_group_id=group.exam_group_id,
            question_id=question_id,
            question_code=question_code,
            question_no=question_no,
            raw_type=group.raw_type,
            stat_type=group.stat_type,
            stem_html=normalize_string(source_question.get("stem")),
            stem_text=html_to_text(source_question.get("stem")),
            content_html=normalize_string(source_question.get("content")),
            content_text=html_to_text(source_question.get("content")),
            source_blank_id=None,
            sort_order=question_sort_order,
            score=1.00,
            status=1,
            delete_flag="1",
        )
        db.add(question)
        db.flush()
        counts["questions"] += 1

        answer_payload = source_question.get("answer")
        if answer_payload is not None:
            db.add(
                ExamQuestionAnswer(
                    exam_question_id=question.exam_question_id,
                    answer_raw=stringify_raw_answer(answer_payload),
                    answer_json=normalize_answer_json(answer_payload),
                    status=1,
                    delete_flag="1",
                )
            )
            counts["answers"] += 1

        question_assets = create_assets_for_owner(
            db,
            paper=paper,
            owner_type="question",
            source_paths=extract_image_sources(question.stem_html) + extract_image_sources(question.content_html),
            section=section,
            group=group,
            question=question,
            asset_role="inline_image",
        )
        counts["assets"] += len(question_assets)


def import_blank_questions(
    db: Session,
    *,
    paper: ExamPaper,
    section: ExamSection,
    group: ExamGroup,
    source_question: dict[str, Any],
    blanks: list[Any],
    counts: dict[str, int],
    question_sort_order: int,
) -> int:
    full_content_html = normalize_string(source_question.get("content"))
    for blank_index, blank_payload in enumerate(blanks, start=1):
        if not isinstance(blank_payload, dict):
            continue

        question_sort_order += 1
        blank_id = normalize_string(blank_payload.get("id")) or f"blank_{blank_index}"
        question_no = parse_question_no(blank_id)
        source_question_id = normalize_string(source_question.get("id"))
        blank_fragment_html = extract_blank_fragment_html(
            full_content_html=full_content_html,
            blank_id=blank_id,
            question_no=question_no,
        )
        question_stem_html = blank_fragment_html or build_blank_fallback_html(blank_id, question_no)
        question_content_html = blank_fragment_html or full_content_html
        question_id = shorten_identifier(
            f"{source_question_id}-{blank_id}" if source_question_id else f"{group.group_id or 'group'}-{blank_id}",
            100,
        )
        question_code = build_question_code(
            paper_code=paper.paper_code,
            section_id=section.section_id,
            question_no=question_no,
            question_id=question_id,
            blank_id=blank_id,
        )

        question = ExamQuestion(
            exam_group_id=group.exam_group_id,
            question_id=question_id,
            question_code=question_code,
            question_no=question_no,
            raw_type=group.raw_type,
            stat_type=group.stat_type,
            stem_html=question_stem_html,
            stem_text=html_to_text(question_stem_html),
            content_html=question_content_html,
            content_text=html_to_text(question_content_html),
            source_blank_id=blank_id,
            sort_order=question_sort_order,
            score=1.00,
            status=1,
            delete_flag="1",
        )
        db.add(question)
        db.flush()
        counts["questions"] += 1

        answer_payload = blank_payload.get("answer")
        if answer_payload is not None:
            db.add(
                ExamQuestionAnswer(
                    exam_question_id=question.exam_question_id,
                    answer_raw=stringify_raw_answer(answer_payload),
                    answer_json=normalize_answer_json(answer_payload),
                    status=1,
                    delete_flag="1",
                )
            )
            counts["answers"] += 1

        db.add(
            ExamQuestionBlank(
                exam_question_id=question.exam_question_id,
                blank_id=blank_id,
                sort_order=blank_index,
                status=1,
                delete_flag="1",
            )
        )
        counts["blanks"] += 1

        question_assets = create_assets_for_owner(
            db,
            paper=paper,
            owner_type="question",
            source_paths=extract_image_sources(question.stem_html) + extract_image_sources(question.content_html),
            section=section,
            group=group,
            question=question,
            asset_role="inline_image",
        )
        counts["assets"] += len(question_assets)

    return question_sort_order


def get_or_create_exam_bank(
    db: Session,
    *,
    bank_code: str,
    bank_name: str,
    subject_type: str,
    source_name: str,
) -> ExamBank:
    bank = db.query(ExamBank).filter(ExamBank.bank_code == bank_code).first()
    if bank is None:
        bank = ExamBank(
            bank_code=bank_code,
            bank_name=bank_name,
            exam_type="IELTS",
            subject_scope=subject_type,
            source_name=source_name,
            status=1,
            delete_flag="1",
        )
        db.add(bank)
        db.flush()
        return bank

    bank.bank_name = bank_name or bank.bank_name
    bank.subject_scope = merge_subject_scope(bank.subject_scope, subject_type)
    bank.source_name = source_name or bank.source_name
    bank.status = 1
    bank.delete_flag = "1"
    db.flush()
    return bank


def merge_subject_scope(existing_scope: str | None, subject_type: str) -> str:
    scope_parts = {item.strip() for item in (existing_scope or "").split(",") if item.strip()}
    scope_parts.add(subject_type)
    ordered_parts = [item for item in ("reading", "listening") if item in scope_parts]
    return ",".join(ordered_parts) if ordered_parts else subject_type


def read_json_from_package(package: ImportPackage, relative_path: str) -> dict[str, Any]:
    normalized_relative_path = normalize_import_path(relative_path)
    virtual_file = package.files.get(normalized_relative_path)
    if virtual_file is None:
        raise ValueError(f"{package.root_path or '.'} 缺少文件：{relative_path}")
    parsed = parse_json_bytes(virtual_file.raw_bytes, file_label=normalized_relative_path)
    if not isinstance(parsed, dict):
        raise ValueError(f"{normalized_relative_path} 顶层必须是 JSON 对象")
    return parsed


def parse_json_bytes(raw_bytes: bytes, *, file_label: str) -> Any:
    try:
        text = raw_bytes.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        raise ValueError(f"{file_label} 必须使用 UTF-8 编码") from exc
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        raise ValueError(f"{file_label} 不是合法 JSON：第 {exc.lineno} 行第 {exc.colno} 列附近有错误") from exc


def resolve_section_payload(*, section_doc: dict[str, Any], expected_section_id: str | None) -> dict[str, Any]:
    passages = section_doc.get("passages")
    if not isinstance(passages, list) or not passages:
        raise ValueError("section JSON 缺少 passages[0]")
    if expected_section_id:
        for item in passages:
            if isinstance(item, dict) and normalize_string(item.get("id")) == expected_section_id:
                return item
    first_item = passages[0]
    if not isinstance(first_item, dict):
        raise ValueError("section JSON 的 passages[0] 必须是对象")
    return first_item


def infer_subject_type(*, manifest: dict[str, Any], package: ImportPackage) -> str:
    candidates = [
        normalize_string(manifest.get("paper")).lower(),
        normalize_string(manifest.get("module")).lower(),
        package.root_path.lower(),
    ]
    for candidate in candidates:
        if "reading" in candidate:
            return "reading"
        if "listening" in candidate:
            return "listening"
    raise ValueError(f"{package.root_path or '.'} 无法判断科目类型（reading/listening）")


def extract_book_and_test_numbers(*, manifest: dict[str, Any], package: ImportPackage) -> tuple[str | None, int | None]:
    paper_name = normalize_string(manifest.get("paper"))
    module_name = normalize_string(manifest.get("module"))
    root_name = PurePosixPath(package.root_path).name
    detected_book: str | None = None
    detected_test: int | None = None

    for candidate in (paper_name, module_name, root_name):
        if not candidate:
            continue
        for pattern in BOOK_TEST_PATTERNS:
            match = pattern.search(candidate)
            if not match:
                continue
            if len(match.groups()) >= 2:
                return str(int(match.group(1))).zfill(2), int(match.group(2))
            if detected_book is None:
                book_match = re.search(r"真题\s*0*(\d{1,2})", candidate, re.I)
                if book_match:
                    detected_book = str(int(book_match.group(1))).zfill(2)
            detected_test = int(match.group(1))
    return detected_book, detected_test


def build_bank_identity(
    *,
    manifest: dict[str, Any],
    package: ImportPackage,
    book_code: str | None,
) -> tuple[str, str]:
    module_name = normalize_string(manifest.get("module"))
    root_name = PurePosixPath(package.root_path).name or "ielts"
    if book_code:
        return f"ielts_{book_code}", f"雅思真题{book_code}"
    prefix_text = module_name.split("-")[0].strip() if module_name else ""
    bank_name = prefix_text or root_name
    bank_code = f"ielts_{slugify_ascii(bank_name) or short_hash(bank_name)}"
    return shorten_identifier(bank_code, 100), bank_name


def build_paper_identity(
    *,
    manifest: dict[str, Any],
    package: ImportPackage,
    subject_type: str,
    book_code: str | None,
    test_no: int | None,
    bank_code: str,
) -> tuple[str, str]:
    paper_name = normalize_string(manifest.get("paper")) or PurePosixPath(package.root_path).name or "IELTS Paper"
    if book_code and test_no is not None:
        return f"{bank_code}_t{test_no}_{subject_type}", paper_name
    slug = slugify_ascii(paper_name) or short_hash(paper_name)
    return shorten_identifier(f"{bank_code}_{slug}_{subject_type}", 100), paper_name


def parse_section_no(value: Any) -> int | None:
    text = normalize_string(value)
    match = re.search(r"(\d+)$", text)
    return int(match.group(1)) if match else None


def extract_group_content_html(group_payload: dict[str, Any]) -> str:
    direct_content = normalize_string(group_payload.get("content"))
    if direct_content:
        return direct_content
    questions = group_payload.get("questions")
    if not isinstance(questions, list):
        return ""
    collected: list[str] = []
    for question in questions:
        if not isinstance(question, dict):
            continue
        content = normalize_string(question.get("content"))
        if content and content not in collected:
            collected.append(content)
    return "\n".join(collected)


def collect_group_blank_ids(group_payload: dict[str, Any]) -> list[str]:
    result: list[str] = []
    questions = group_payload.get("questions")
    if not isinstance(questions, list):
        return result
    for question in questions:
        if not isinstance(question, dict):
            continue
        blanks = question.get("blanks")
        if not isinstance(blanks, list):
            continue
        for blank in blanks:
            if isinstance(blank, dict):
                blank_id = normalize_string(blank.get("id"))
                if blank_id:
                    result.append(blank_id)
    return result


def collect_group_question_ids(group_payload: dict[str, Any]) -> list[str]:
    result: list[str] = []
    questions = group_payload.get("questions")
    if not isinstance(questions, list):
        return result
    for question in questions:
        if isinstance(question, dict):
            question_id = normalize_string(question.get("id"))
            if question_id:
                result.append(question_id)
    return result


def infer_stat_type(*, raw_type: str | None, group_payload: dict[str, Any]) -> str | None:
    raw_type_text = (raw_type or "").strip().lower()
    combined_text = " ".join(
        filter(
            None,
            [
                html_to_text(group_payload.get("instructions")).lower(),
                html_to_text(extract_group_content_html(group_payload)).lower(),
                normalize_string(group_payload.get("title")).lower(),
            ],
        )
    )
    if raw_type_text == "tfng":
        return "true_false_not_given"
    if raw_type_text == "matching":
        return "matching"
    if raw_type_text == "single":
        return "single_choice"
    if raw_type_text == "multiple":
        return "multiple_choice"
    if raw_type_text != "cloze_inline":
        return raw_type_text or None
    if "choose two letters" in combined_text or "choose three letters" in combined_text:
        return "multiple_choice"
    if "choose the correct letter" in combined_text:
        return "single_choice"
    if "flow-chart" in combined_text or "flow chart" in combined_text:
        return "flow_chart_completion"
    if "diagram" in combined_text:
        return "diagram_label_completion"
    if "table" in combined_text:
        return "table_completion"
    if "summary" in combined_text:
        return "summary_completion"
    if "note" in combined_text or "notes" in combined_text:
        return "note_completion"
    if "sentence" in combined_text:
        return "sentence_completion"
    if "choose" in combined_text and ("box" in combined_text or "list" in combined_text):
        return "matching"
    return "fill_in_blank"


def parse_question_no(value: Any) -> int | None:
    text = normalize_string(value)
    match = QUESTION_NUMBER_PATTERN.search(text)
    return int(match.group(1)) if match else None


def build_question_code(
    *,
    paper_code: str,
    section_id: str | None,
    question_no: int | None,
    question_id: str,
    blank_id: str | None,
) -> str:
    section_part = slugify_ascii(section_id or "section") or "section"
    if blank_id and question_no is not None:
        return shorten_identifier(f"{paper_code}_{section_part}_b{question_no}", 100)
    if question_no is not None:
        return shorten_identifier(f"{paper_code}_{section_part}_q{question_no}", 100)
    raw_part = slugify_ascii(blank_id or question_id) or short_hash(blank_id or question_id)
    return shorten_identifier(f"{paper_code}_{section_part}_{raw_part}", 100)


def extract_blank_fragment_html(*, full_content_html: str, blank_id: str, question_no: int | None) -> str:
    if not full_content_html:
        return build_blank_fallback_html(blank_id, question_no)
    safe_blank_id = re.escape(blank_id)
    for tag in ("p", "li", "tr", "td", "th"):
        pattern = re.compile(rf"(<{tag}\b[^>]*>.*?\[\[{safe_blank_id}\]\].*?</{tag}>)", re.I | re.S)
        match = pattern.search(full_content_html)
        if match:
            return replace_blank_placeholder(match.group(1), blank_id)
    if f"[[{blank_id}]]" in full_content_html:
        return replace_blank_placeholder(full_content_html, blank_id)
    return build_blank_fallback_html(blank_id, question_no)


def build_blank_fallback_html(blank_id: str, question_no: int | None) -> str:
    if question_no is not None:
        return f"<p>Question {question_no}: _____</p>"
    return f"<p>{blank_id}: _____</p>"


def replace_blank_placeholder(content_html: str, blank_id: str) -> str:
    return content_html.replace(f"[[{blank_id}]]", "_____")


def html_to_text(value: Any) -> str:
    html = normalize_string(value)
    if not html:
        return ""
    parser = HtmlTextExtractor()
    parser.feed(html)
    parser.close()
    return parser.get_text()


def extract_image_sources(value: Any) -> list[str]:
    html = normalize_string(value)
    if not html:
        return []
    result: list[str] = []
    seen: set[str] = set()
    for item in IMAGE_SOURCE_PATTERN.findall(html):
        normalized = normalize_string(item)
        if normalized and normalized not in seen:
            seen.add(normalized)
            result.append(normalized)
    return result


def extract_group_shared_options(group_payload: dict[str, Any]) -> list[str]:
    explicit_options = group_payload.get("options")
    if isinstance(explicit_options, list) and explicit_options:
        return [
            normalized_option
            for raw_option in explicit_options
            if (normalized_option := normalize_string(raw_option))
        ]

    instructions_html = normalize_string(group_payload.get("instructions"))
    if not instructions_html:
        return []

    extracted_options: list[str] = []
    seen_option_keys: set[str] = set()
    for match in OPTION_HTML_BLOCK_PATTERN.finditer(instructions_html):
        option_html = normalize_string(match.group(1))
        option_text = html_to_text(option_html)
        parsed_option = parse_option_prefix(option_text)
        if not parsed_option:
            continue

        option_key, _, _ = parsed_option
        if option_key in seen_option_keys:
            continue

        seen_option_keys.add(option_key)
        extracted_options.append(option_html)

    for match in OPTION_SIMPLE_TABLE_CELL_PATTERN.finditer(instructions_html):
        option_text = normalize_string(match.group("content"))
        parsed_option = parse_option_prefix(
            option_text,
            allow_compact=True,
        )
        if not parsed_option:
            continue

        option_key, option_content, _ = parsed_option
        if option_key in seen_option_keys:
            continue

        seen_option_keys.add(option_key)
        extracted_options.append(f"<p>{option_key} {option_content}</p>")

    if extracted_options:
        return extracted_options

    for line in html_to_text(instructions_html).splitlines():
        option_text = normalize_string(line)
        parsed_option = parse_option_prefix(option_text)
        if not parsed_option:
            continue

        option_key, option_content, _ = parsed_option
        if option_key in seen_option_keys:
            continue

        seen_option_keys.add(option_key)
        extracted_options.append(f"<p>{option_key} {option_content}</p>")

    return extracted_options


def looks_like_option_text(value: str) -> bool:
    text = normalize_string(value)
    if not text:
        return False
    return parse_option_prefix(text) is not None


def create_assets_for_owner(
    db: Session,
    *,
    paper: ExamPaper,
    owner_type: str,
    source_paths: list[str],
    asset_role: str,
    section: ExamSection | None = None,
    group: ExamGroup | None = None,
    question: ExamQuestion | None = None,
) -> list[ExamAsset]:
    created_assets: list[ExamAsset] = []
    deduped_paths: list[str] = []
    seen_paths: set[str] = set()
    for raw_source_path in source_paths:
        normalized_source_path = normalize_string(raw_source_path)
        if normalized_source_path and normalized_source_path not in seen_paths:
            seen_paths.add(normalized_source_path)
            deduped_paths.append(normalized_source_path)
    for index, source_path in enumerate(deduped_paths, start=1):
        role = asset_role if index == 1 else "inline_image"
        storage_path = build_asset_storage_path(
            paper=paper,
            owner_type=owner_type,
            source_path=source_path,
            section=section,
            group=group,
            question=question,
        )
        asset = ExamAsset(
            exam_section_id=section.exam_section_id if section else None,
            exam_group_id=group.exam_group_id if group else None,
            exam_question_id=question.exam_question_id if question else None,
            owner_type=owner_type,
            asset_type=infer_asset_type(source_path),
            asset_role=role,
            asset_name=PurePosixPath(source_path).name or f"asset_{index}",
            source_path=source_path,
            storage_path=storage_path,
            asset_url=f"/{storage_path}",
            sort_order=index,
            status=1,
            delete_flag="1",
        )
        db.add(asset)
        db.flush()
        created_assets.append(asset)
    return created_assets


def infer_asset_type(source_path: str) -> str:
    suffix = PurePosixPath(source_path).suffix.lower()
    if suffix in {".mp3", ".wav", ".m4a", ".aac"}:
        return "audio"
    if suffix in {".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".svg"}:
        return "image"
    if suffix == ".pdf":
        return "pdf"
    return "other"


def build_asset_storage_path(
    *,
    paper: ExamPaper,
    owner_type: str,
    source_path: str,
    section: ExamSection | None,
    group: ExamGroup | None,
    question: ExamQuestion | None,
) -> str:
    owner_segment = owner_type
    if owner_type == "section" and section is not None:
        owner_segment = f"section-{section.section_no or section.sort_order}"
    elif owner_type == "group" and group is not None:
        owner_segment = f"group-{group.sort_order}"
    elif owner_type == "question" and question is not None:
        owner_segment = f"question-{question.question_no or question.sort_order}"
    source_name = PurePosixPath(source_path).name or short_hash(source_path)
    return normalize_import_path(f"exam-assets/{paper.paper_code}/{owner_segment}/{source_name}")


def stringify_raw_answer(answer: Any) -> str:
    if isinstance(answer, str):
        return answer
    return json.dumps(answer, ensure_ascii=False)


def normalize_answer_json(answer: Any) -> list[str]:
    raw_values = answer if isinstance(answer, list) else [answer]
    normalized_values: list[str] = []
    seen: set[str] = set()
    for raw_value in raw_values:
        text = normalize_string(raw_value)
        if not text:
            continue
        for part in re.split(r"[|/;\n]+", text):
            normalized_part = normalize_answer_token(part)
            if normalized_part and normalized_part not in seen:
                seen.add(normalized_part)
                normalized_values.append(normalized_part)
    return normalized_values


def normalize_answer_token(value: str) -> str:
    normalized = " ".join(value.strip().split())
    if not normalized:
        return ""
    upper_text = normalized.upper()
    if upper_text in SPECIAL_ANSWER_TOKENS or re.fullmatch(r"[A-Z]", upper_text):
        return upper_text
    return normalized.lower()


def _legacy_extract_option_key(option_html: str, *, fallback_index: int) -> str:
    text = normalize_string(option_html)
    match = OPTION_KEY_PATTERN.match(text)
    if match:
        return match.group(1).upper()
    fallback_index = max(1, fallback_index)
    return chr(ord("A") + fallback_index - 1)


def _legacy_strip_option_prefix(option_text: str, *, option_key: str) -> str:
    text = normalize_string(option_text)
    if not text:
        return ""
    pattern = re.compile(rf"^\s*{re.escape(option_key)}[\.\)\-:：]?\s+", re.I)
    return pattern.sub("", text).strip()


def extract_option_key(option_html: str, *, fallback_index: int) -> str:
    text = normalize_string(option_html)
    parsed_option = parse_option_prefix(
        text,
        allow_compact=True,
    )
    if parsed_option:
        return parsed_option[0]
    fallback_index = max(1, fallback_index)
    return chr(ord("A") + fallback_index - 1)


def strip_option_prefix(option_text: str, *, option_key: str) -> str:
    text = normalize_string(option_text)
    if not text:
        return ""
    parsed_option = parse_option_prefix(
        text,
        allow_compact=True,
    )
    if parsed_option and parsed_option[0] == normalize_option_key(option_key):
        return parsed_option[1]
    return text


def parse_option_prefix(
    value: str,
    *,
    allow_compact: bool = False,
) -> tuple[str, str, bool] | None:
    text = normalize_string(value)
    if not text:
        return None

    prefix_match = OPTION_PREFIX_PATTERN.match(text)
    if prefix_match:
        return (
            normalize_option_key(prefix_match.group("key")),
            normalize_string(prefix_match.group("content")),
            False,
        )

    if allow_compact and "\n" not in text and "\r" not in text:
        compact_match = COMPACT_TABLE_OPTION_PATTERN.match(text)
        if compact_match:
            return (
                normalize_option_key(compact_match.group("key")),
                normalize_string(compact_match.group("content")),
                True,
            )

    return None


def normalize_option_key(value: str) -> str:
    text = normalize_string(value)
    if re.fullmatch(r"[ivxlcdm]+", text, re.I) and text == text.lower():
        return text.lower()
    return text.upper()


def normalize_import_path(value: str | None) -> str:
    text = (value or "").replace("\\", "/").strip()
    if not text:
        return ""
    text = re.sub(r"^[A-Za-z]:", "", text)
    parts: list[str] = []
    for part in text.split("/"):
        segment = part.strip()
        if not segment or segment == ".":
            continue
        if segment == "..":
            if parts:
                parts.pop()
            continue
        parts.append(segment)
    return "/".join(parts)


def normalize_string(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()


def slugify_ascii(value: str | None) -> str:
    text = normalize_string(value)
    if not text:
        return ""
    ascii_text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii")
    ascii_text = re.sub(r"[^a-zA-Z0-9]+", "_", ascii_text.lower())
    return ascii_text.strip("_")


def short_hash(value: str) -> str:
    return hashlib.md5(value.encode("utf-8")).hexdigest()[:8]


def shorten_identifier(value: str, max_length: int) -> str:
    normalized = normalize_string(value)
    if len(normalized) <= max_length:
        return normalized
    digest = short_hash(normalized)
    keep_length = max_length - len(digest) - 1
    return f"{normalized[:keep_length].rstrip('_')}_{digest}"
