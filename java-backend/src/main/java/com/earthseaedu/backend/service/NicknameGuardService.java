package com.earthseaedu.backend.service;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.dto.auth.AuthResponses;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.NicknameAuditLogMapper;
import com.earthseaedu.backend.mapper.NicknameContactPatternMapper;
import com.earthseaedu.backend.mapper.NicknameRuleGroupMapper;
import com.earthseaedu.backend.mapper.NicknameRulePublishLogMapper;
import com.earthseaedu.backend.mapper.NicknameWordRuleMapper;
import com.earthseaedu.backend.mapper.UserMapper;
import com.earthseaedu.backend.model.auth.User;
import com.earthseaedu.backend.model.nickname.NicknameAuditLog;
import com.earthseaedu.backend.model.nickname.NicknameContactPattern;
import com.earthseaedu.backend.model.nickname.NicknameRuleGroup;
import com.earthseaedu.backend.model.nickname.NicknameRulePublishLog;
import com.earthseaedu.backend.model.nickname.NicknameWordRule;
import com.earthseaedu.backend.support.PageResult;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NicknameGuardService {

    private static final Pattern ZERO_WIDTH_PATTERN = Pattern.compile("[\\u200B-\\u200F\\u2060\\uFEFF]");
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[\\s\\-_.,·|/\\\\]+");

    private final UserMapper userMapper;
    private final NicknameRuleGroupMapper nicknameRuleGroupMapper;
    private final NicknameWordRuleMapper nicknameWordRuleMapper;
    private final NicknameContactPatternMapper nicknameContactPatternMapper;
    private final NicknameRulePublishLogMapper nicknameRulePublishLogMapper;
    private final NicknameAuditLogMapper nicknameAuditLogMapper;

    public NicknameGuardService(
        UserMapper userMapper,
        NicknameRuleGroupMapper nicknameRuleGroupMapper,
        NicknameWordRuleMapper nicknameWordRuleMapper,
        NicknameContactPatternMapper nicknameContactPatternMapper,
        NicknameRulePublishLogMapper nicknameRulePublishLogMapper,
        NicknameAuditLogMapper nicknameAuditLogMapper
    ) {
        this.userMapper = userMapper;
        this.nicknameRuleGroupMapper = nicknameRuleGroupMapper;
        this.nicknameWordRuleMapper = nicknameWordRuleMapper;
        this.nicknameContactPatternMapper = nicknameContactPatternMapper;
        this.nicknameRulePublishLogMapper = nicknameRulePublishLogMapper;
        this.nicknameAuditLogMapper = nicknameAuditLogMapper;
    }

    public AuthResponses.AvailabilityCheckResponse checkNicknameAvailability(
        String userId,
        String nickname,
        String clientIp,
        String userAgent
    ) {
        NicknameCheckResult result = checkNicknameAvailabilityInternal(
            userId,
            nickname,
            "check",
            true,
            clientIp,
            userAgent
        );
        return new AuthResponses.AvailabilityCheckResponse(result.available(), result.message());
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResponses.NicknameResponse updateNicknameForUser(
        String userId,
        String nickname,
        String clientIp,
        String userAgent
    ) {
        NicknameCheckResult result = checkNicknameAvailabilityInternal(
            userId,
            nickname,
            "update",
            false,
            clientIp,
            userAgent
        );
        if (!result.available()) {
            throw badRequest(result.message());
        }

        User user = userMapper.findActiveById(userId);
        if (user == null) {
            throw badRequest("用户不存在");
        }

        String normalizedNickname = CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(nickname));
        user.setNickname(normalizedNickname);
        user.setUpdateTime(nowUtc());
        userMapper.update(user);

        createNicknameAuditLog(
            "update",
            nickname,
            new NicknameGuardHit(
                "pass",
                "昵称修改成功",
                normalizedNickname,
                "system",
                null,
                null,
                null,
                null
            ),
            userId,
            null,
            clientIp,
            userAgent,
            null
        );
        return new AuthResponses.NicknameResponse(user.getId(), user.getNickname());
    }

    public List<NicknameRuleGroup> listNicknameRuleGroups(String status, String groupType) {
        return nicknameRuleGroupMapper.list(blankToNull(status), blankToNull(groupType));
    }

    @Transactional(rollbackFor = Exception.class)
    public NicknameRuleGroup createNicknameRuleGroup(
        String groupCode,
        String groupName,
        String groupType,
        String scope,
        String status,
        Integer priority,
        String description
    ) {
        String normalizedCode = CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(groupCode));
        if (CharSequenceUtil.isBlank(normalizedCode)) {
            throw badRequest("group_code 不能为空");
        }
        if (nicknameRuleGroupMapper.findActiveByGroupCode(normalizedCode) != null) {
            throw badRequest("该规则分组编码已存在");
        }

        NicknameRuleGroup row = new NicknameRuleGroup();
        row.setGroupCode(normalizedCode);
        row.setGroupName(requiredTrimmed(groupName, "group_name 不能为空"));
        row.setGroupType(requiredTrimmed(groupType, "group_type 不能为空"));
        row.setScope(defaultTrimmed(scope, "nickname"));
        row.setStatus(defaultTrimmed(status, "draft"));
        row.setPriority(priority == null ? 100 : priority);
        row.setDescription(trimToNull(description));
        row.setVersionNo(1);
        row.setDeleteFlag("1");
        row.setCreateTime(nowUtc());
        row.setUpdateTime(row.getCreateTime());
        nicknameRuleGroupMapper.insert(row);
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public NicknameWordRule createNicknameWordRule(
        Integer groupId,
        String word,
        String matchType,
        String decision,
        String status,
        Integer priority,
        String riskLevel,
        String source,
        String note
    ) {
        NicknameRuleGroup group = nicknameRuleGroupMapper.findActiveById(groupId);
        if (group == null) {
            throw badRequest("规则分组不存在");
        }

        String normalizedWord = normalizeRuleWord(word);
        if (CharSequenceUtil.isBlank(normalizedWord)) {
            throw badRequest("词条不能为空");
        }

        String normalizedMatchType = defaultTrimmed(matchType, "contains");
        if (nicknameWordRuleMapper.findDuplicate(groupId, normalizedWord, normalizedMatchType) != null) {
            throw badRequest("该词条规则已存在");
        }

        NicknameWordRule row = new NicknameWordRule();
        row.setGroupId(groupId);
        row.setWord(requiredTrimmed(word, "词条不能为空"));
        row.setNormalizedWord(normalizedWord);
        row.setMatchType(normalizedMatchType);
        row.setDecision(defaultTrimmed(decision, "reject"));
        row.setStatus(defaultTrimmed(status, "draft"));
        row.setPriority(priority == null ? 100 : priority);
        row.setRiskLevel(defaultTrimmed(riskLevel, "medium"));
        row.setSource(defaultTrimmed(source, "manual"));
        row.setNote(trimToNull(note));
        row.setVersionNo(1);
        row.setDeleteFlag("1");
        row.setCreateTime(nowUtc());
        row.setUpdateTime(row.getCreateTime());
        nicknameWordRuleMapper.insert(row);
        return row;
    }

    public PageResult<NicknameWordRule> listNicknameWordRules(
        Integer groupId,
        String status,
        String decision,
        String keyword,
        int limit
    ) {
        int safeLimit = normalizeLimit(limit);
        String normalizedKeyword = CharSequenceUtil.isBlank(keyword)
            ? null
            : normalizeNicknameForCheck(keyword.trim());
        List<NicknameWordRule> rows = nicknameWordRuleMapper.list(
            groupId,
            blankToNull(status),
            blankToNull(decision),
            blankToNull(keyword),
            normalizedKeyword,
            safeLimit
        );
        long total = nicknameWordRuleMapper.count(
            groupId,
            blankToNull(status),
            blankToNull(decision),
            blankToNull(keyword),
            normalizedKeyword
        );
        return new PageResult<>(total, rows);
    }

    @Transactional(rollbackFor = Exception.class)
    public NicknameContactPattern createNicknameContactPattern(
        String patternName,
        String patternType,
        String patternRegex,
        Integer groupId,
        String decision,
        String status,
        Integer priority,
        String riskLevel,
        String normalizedHint,
        String note
    ) {
        if (groupId != null && nicknameRuleGroupMapper.findActiveById(groupId) == null) {
            throw badRequest("规则分组不存在");
        }

        String normalizedName = CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(patternName));
        if (CharSequenceUtil.isBlank(normalizedName)) {
            throw badRequest("pattern_name 不能为空");
        }
        String normalizedPatternType = requiredTrimmed(patternType, "pattern_type 不能为空");
        String normalizedPatternRegex = requiredTrimmed(patternRegex, "pattern_regex 不能为空");
        if (nicknameContactPatternMapper.findDuplicate(normalizedName, normalizedPatternType) != null) {
            throw badRequest("该联系方式规则已存在");
        }

        NicknameContactPattern row = new NicknameContactPattern();
        row.setGroupId(groupId);
        row.setPatternName(normalizedName);
        row.setPatternType(normalizedPatternType);
        row.setPatternRegex(normalizedPatternRegex);
        row.setDecision(defaultTrimmed(decision, "reject"));
        row.setStatus(defaultTrimmed(status, "draft"));
        row.setPriority(priority == null ? 100 : priority);
        row.setRiskLevel(defaultTrimmed(riskLevel, "high"));
        row.setNormalizedHint(trimToNull(normalizedHint));
        row.setNote(trimToNull(note));
        row.setVersionNo(1);
        row.setDeleteFlag("1");
        row.setCreateTime(nowUtc());
        row.setUpdateTime(row.getCreateTime());
        nicknameContactPatternMapper.insert(row);
        return row;
    }

    public PageResult<NicknameContactPattern> listNicknameContactPatterns(
        Integer groupId,
        String status,
        String patternType,
        String keyword,
        int limit
    ) {
        int safeLimit = normalizeLimit(limit);
        List<NicknameContactPattern> rows = nicknameContactPatternMapper.list(
            groupId,
            blankToNull(status),
            blankToNull(patternType),
            blankToNull(keyword),
            safeLimit
        );
        long total = nicknameContactPatternMapper.count(
            groupId,
            blankToNull(status),
            blankToNull(patternType),
            blankToNull(keyword)
        );
        return new PageResult<>(total, rows);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateNicknameRuleTargetStatus(
        String targetType,
        Integer targetId,
        String status
    ) {
        String normalizedTargetType = defaultTrimmed(targetType, "");
        String normalizedStatus = requiredTrimmed(status, "status 不能为空");
        LocalDateTime now = nowUtc();

        if ("group".equals(normalizedTargetType)) {
            NicknameRuleGroup row = nicknameRuleGroupMapper.findActiveById(targetId);
            if (row == null) {
                throw badRequest("目标规则不存在");
            }
            row.setStatus(normalizedStatus);
            row.setUpdateTime(now);
            nicknameRuleGroupMapper.update(row);
            return buildStatusPayload(row.getId(), row.getStatus(), row.getUpdateTime());
        }
        if ("word".equals(normalizedTargetType)) {
            NicknameWordRule row = nicknameWordRuleMapper.findActiveById(targetId);
            if (row == null) {
                throw badRequest("目标规则不存在");
            }
            row.setStatus(normalizedStatus);
            row.setUpdateTime(now);
            nicknameWordRuleMapper.update(row);
            return buildStatusPayload(row.getId(), row.getStatus(), row.getUpdateTime());
        }
        if ("pattern".equals(normalizedTargetType)) {
            NicknameContactPattern row = nicknameContactPatternMapper.findActiveById(targetId);
            if (row == null) {
                throw badRequest("目标规则不存在");
            }
            row.setStatus(normalizedStatus);
            row.setUpdateTime(now);
            nicknameContactPatternMapper.update(row);
            return buildStatusPayload(row.getId(), row.getStatus(), row.getUpdateTime());
        }
        throw badRequest("target_type 仅支持 group / word / pattern");
    }

    public PageResult<NicknameAuditLog> listNicknameAuditLogs(
        String decision,
        String scene,
        String hitGroupCode,
        int limit
    ) {
        int safeLimit = normalizeLimit(limit);
        List<NicknameAuditLog> rows = nicknameAuditLogMapper.list(
            blankToNull(decision),
            blankToNull(scene),
            blankToNull(hitGroupCode),
            safeLimit
        );
        long total = nicknameAuditLogMapper.count(
            blankToNull(decision),
            blankToNull(scene),
            blankToNull(hitGroupCode)
        );
        return new PageResult<>(total, rows);
    }

    public Map<String, Object> toRuleGroupPayload(NicknameRuleGroup row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", row.getId());
        payload.put("group_code", row.getGroupCode());
        payload.put("group_name", row.getGroupName());
        payload.put("group_type", row.getGroupType());
        payload.put("scope", row.getScope());
        payload.put("status", row.getStatus());
        payload.put("priority", row.getPriority());
        payload.put("description", row.getDescription());
        payload.put("version_no", row.getVersionNo());
        payload.put("create_time", row.getCreateTime());
        payload.put("update_time", row.getUpdateTime());
        return payload;
    }

    public Map<String, Object> toWordRulePayload(NicknameWordRule row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", row.getId());
        payload.put("group_id", row.getGroupId());
        payload.put("word", row.getWord());
        payload.put("normalized_word", row.getNormalizedWord());
        payload.put("match_type", row.getMatchType());
        payload.put("decision", row.getDecision());
        payload.put("status", row.getStatus());
        payload.put("priority", row.getPriority());
        payload.put("risk_level", row.getRiskLevel());
        payload.put("source", row.getSource());
        payload.put("note", row.getNote());
        payload.put("version_no", row.getVersionNo());
        payload.put("create_time", row.getCreateTime());
        payload.put("update_time", row.getUpdateTime());
        return payload;
    }

    public Map<String, Object> toContactPatternPayload(NicknameContactPattern row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", row.getId());
        payload.put("group_id", row.getGroupId());
        payload.put("pattern_name", row.getPatternName());
        payload.put("pattern_type", row.getPatternType());
        payload.put("pattern_regex", row.getPatternRegex());
        payload.put("decision", row.getDecision());
        payload.put("status", row.getStatus());
        payload.put("priority", row.getPriority());
        payload.put("risk_level", row.getRiskLevel());
        payload.put("normalized_hint", row.getNormalizedHint());
        payload.put("note", row.getNote());
        payload.put("version_no", row.getVersionNo());
        payload.put("create_time", row.getCreateTime());
        payload.put("update_time", row.getUpdateTime());
        return payload;
    }

    public Map<String, Object> toAuditLogPayload(NicknameAuditLog row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", row.getId());
        payload.put("trace_id", row.getTraceId());
        payload.put("user_id", row.getUserId());
        payload.put("scene", row.getScene());
        payload.put("raw_nickname", row.getRawNickname());
        payload.put("normalized_nickname", row.getNormalizedNickname());
        payload.put("decision", row.getDecision());
        payload.put("hit_source", row.getHitSource());
        payload.put("hit_rule_id", row.getHitRuleId());
        payload.put("hit_pattern_id", row.getHitPatternId());
        payload.put("hit_group_code", row.getHitGroupCode());
        payload.put("hit_content", row.getHitContent());
        payload.put("message", row.getMessage());
        payload.put("client_ip", row.getClientIp());
        payload.put("user_agent", row.getUserAgent());
        payload.put("app_version", row.getAppVersion());
        payload.put("rule_version_batch", row.getRuleVersionBatch());
        payload.put("extra_json", parseJson(row.getExtraJson()));
        payload.put("create_time", row.getCreateTime());
        return payload;
    }

    private NicknameCheckResult checkNicknameAvailabilityInternal(
        String userId,
        String nickname,
        String scene,
        boolean writeAuditLog,
        String clientIp,
        String userAgent
    ) {
        User user = userMapper.findActiveById(userId);
        if (user == null) {
            throw badRequest("用户不存在");
        }
        if (!"active".equals(user.getStatus())) {
            throw badRequest("用户已被禁用");
        }

        String normalizedNickname = CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(nickname));
        if (CharSequenceUtil.isBlank(normalizedNickname)) {
            NicknameGuardHit guardHit = new NicknameGuardHit("reject", "请输入昵称", "", "system", null, null, null, null);
            if (writeAuditLog) {
                createNicknameAuditLog(scene, nickname, guardHit, userId, null, clientIp, userAgent, null);
            }
            return new NicknameCheckResult(false, "请输入昵称");
        }
        if (normalizedNickname.length() > 100) {
            NicknameGuardHit guardHit = new NicknameGuardHit(
                "reject",
                "昵称长度不能超过 100 位",
                normalizedNickname,
                "system",
                null,
                null,
                null,
                null
            );
            if (writeAuditLog) {
                createNicknameAuditLog(scene, nickname, guardHit, userId, null, clientIp, userAgent, null);
            }
            return new NicknameCheckResult(false, "昵称长度不能超过 100 位");
        }
        if (CharSequenceUtil.equals(normalizedNickname, CharSequenceUtil.nullToEmpty(user.getNickname()))) {
            NicknameGuardHit guardHit = new NicknameGuardHit(
                "reject",
                "该昵称与当前昵称相同",
                normalizedNickname,
                "system",
                null,
                null,
                null,
                null
            );
            if (writeAuditLog) {
                createNicknameAuditLog(scene, nickname, guardHit, userId, null, clientIp, userAgent, null);
            }
            return new NicknameCheckResult(false, "该昵称与当前昵称相同");
        }

        NicknameGuardHit guardHit = evaluateNicknameGuard(normalizedNickname);
        if (!guardHit.passed()) {
            if (writeAuditLog) {
                createNicknameAuditLog(scene, nickname, guardHit, userId, null, clientIp, userAgent, null);
            }
            return new NicknameCheckResult(false, guardHit.message());
        }

        if (userMapper.countByNicknameExcludingUserId(normalizedNickname, userId) > 0) {
            NicknameGuardHit duplicateHit = new NicknameGuardHit(
                "reject",
                "该昵称已被占用",
                normalizedNickname,
                "system",
                null,
                null,
                null,
                null
            );
            if (writeAuditLog) {
                createNicknameAuditLog(scene, nickname, duplicateHit, userId, null, clientIp, userAgent, null);
            }
            return new NicknameCheckResult(false, "该昵称已被占用");
        }

        if (writeAuditLog) {
            createNicknameAuditLog(scene, nickname, guardHit, userId, null, clientIp, userAgent, null);
        }
        return new NicknameCheckResult(true, "该昵称可以使用");
    }

    private NicknameGuardHit evaluateNicknameGuard(String nickname) {
        String normalizedNickname = normalizeNicknameForCheck(nickname);
        if (CharSequenceUtil.isBlank(normalizedNickname)) {
            return new NicknameGuardHit("reject", "请输入昵称", normalizedNickname, "system", null, null, null, null);
        }

        List<NicknameWordRuleRuntime> wordRules = getActiveNicknameWordRules();
        List<NicknameContactPatternRuntime> contactPatterns = getActiveNicknameContactPatterns();

        boolean hasWhitelistHit = wordRules.stream()
            .anyMatch(rule -> "whitelist".equals(rule.groupType()) && matchWordRule(normalizedNickname, rule));

        for (NicknameWordRuleRuntime rule : wordRules) {
            if ("whitelist".equals(rule.groupType())) {
                continue;
            }
            if (!"reject".equals(rule.decision())) {
                continue;
            }
            if (!matchWordRule(normalizedNickname, rule)) {
                continue;
            }
            return buildHitFromWordRule(normalizedNickname, rule);
        }

        for (NicknameContactPatternRuntime pattern : contactPatterns) {
            Matcher matcher = Pattern.compile(pattern.patternRegex(), Pattern.CASE_INSENSITIVE).matcher(normalizedNickname);
            if (matcher.find()) {
                return buildHitFromContactPattern(normalizedNickname, pattern, matcher.group(0));
            }
        }

        for (NicknameWordRuleRuntime rule : wordRules) {
            if ("whitelist".equals(rule.groupType())) {
                continue;
            }
            if (!"review".equals(rule.decision())) {
                continue;
            }
            if (!matchWordRule(normalizedNickname, rule)) {
                continue;
            }
            if (hasWhitelistHit && !"reserved".equals(rule.groupType()) && !"contact".equals(rule.groupType())) {
                continue;
            }
            return buildHitFromWordRule(normalizedNickname, rule);
        }

        return new NicknameGuardHit("pass", "昵称可使用", normalizedNickname, null, null, null, null, null);
    }

    private List<NicknameWordRuleRuntime> getActiveNicknameWordRules() {
        LocalDateTime now = nowUtc();
        return nicknameWordRuleMapper.listRuntimeRuleMaps()
            .stream()
            .filter(row -> isRuleEffective(
                asString(row.get("groupStatus")),
                asString(row.get("groupDeleteFlag")),
                asLocalDateTime(row.get("groupEffectiveStartTime")),
                asLocalDateTime(row.get("groupEffectiveEndTime")),
                now
            ))
            .filter(row -> isRuleEffective(
                asString(row.get("ruleStatus")),
                asString(row.get("ruleDeleteFlag")),
                asLocalDateTime(row.get("ruleEffectiveStartTime")),
                asLocalDateTime(row.get("ruleEffectiveEndTime")),
                now
            ))
            .map(row -> new NicknameWordRuleRuntime(
                Convert.toInt(row.get("ruleId")),
                asString(row.get("groupCode")),
                asString(row.get("groupType")),
                asString(row.get("word")),
                asString(row.get("normalizedWord")),
                asString(row.get("matchType")),
                asString(row.get("decision")),
                Convert.toInt(row.get("priority"), 100),
                asString(row.get("riskLevel"))
            ))
            .toList();
    }

    private List<NicknameContactPatternRuntime> getActiveNicknameContactPatterns() {
        LocalDateTime now = nowUtc();
        return nicknameContactPatternMapper.listRuntimePatternMaps()
            .stream()
            .filter(row -> {
                Integer groupId = Convert.toInt(row.get("groupId"), null);
                return groupId == null || isRuleEffective(
                    asString(row.get("groupStatus")),
                    asString(row.get("groupDeleteFlag")),
                    asLocalDateTime(row.get("groupEffectiveStartTime")),
                    asLocalDateTime(row.get("groupEffectiveEndTime")),
                    now
                );
            })
            .filter(row -> isRuleEffective(
                asString(row.get("patternStatus")),
                asString(row.get("patternDeleteFlag")),
                asLocalDateTime(row.get("patternEffectiveStartTime")),
                asLocalDateTime(row.get("patternEffectiveEndTime")),
                now
            ))
            .map(row -> new NicknameContactPatternRuntime(
                Convert.toInt(row.get("patternId")),
                asString(row.get("groupCode")),
                asString(row.get("patternName")),
                asString(row.get("patternType")),
                asString(row.get("patternRegex")),
                asString(row.get("decision")),
                Convert.toInt(row.get("priority"), 100),
                asString(row.get("riskLevel"))
            ))
            .toList();
    }

    private boolean matchWordRule(String normalizedNickname, NicknameWordRuleRuntime rule) {
        if (CharSequenceUtil.isBlank(rule.normalizedWord())) {
            return false;
        }
        return switch (CharSequenceUtil.nullToEmpty(rule.matchType())) {
            case "exact" -> CharSequenceUtil.equals(normalizedNickname, rule.normalizedWord());
            case "prefix" -> normalizedNickname.startsWith(rule.normalizedWord());
            case "suffix" -> normalizedNickname.endsWith(rule.normalizedWord());
            case "regex" -> Pattern.compile(rule.normalizedWord()).matcher(normalizedNickname).find();
            default -> normalizedNickname.contains(rule.normalizedWord());
        };
    }

    private NicknameGuardHit buildHitFromWordRule(String normalizedNickname, NicknameWordRuleRuntime rule) {
        return new NicknameGuardHit(
            rule.decision(),
            getDecisionMessage(rule.decision(), rule.groupType()),
            normalizedNickname,
            "word_rule",
            rule.ruleId(),
            null,
            rule.groupCode(),
            rule.word()
        );
    }

    private NicknameGuardHit buildHitFromContactPattern(
        String normalizedNickname,
        NicknameContactPatternRuntime pattern,
        String matchContent
    ) {
        return new NicknameGuardHit(
            pattern.decision(),
            getDecisionMessage(pattern.decision(), null),
            normalizedNickname,
            "contact_pattern",
            null,
            pattern.patternId(),
            pattern.groupCode(),
            matchContent
        );
    }

    private String getDecisionMessage(String decision, String groupType) {
        if ("reject".equals(decision)) {
            if ("reserved".equals(groupType)) {
                return "昵称包含受保护内容，请重新输入";
            }
            return "昵称包含违规内容，请重新输入";
        }
        if ("review".equals(decision)) {
            return "昵称存在风险，请重新输入";
        }
        return "昵称可使用";
    }

    private boolean isRuleEffective(
        String status,
        String deleteFlag,
        LocalDateTime effectiveStartTime,
        LocalDateTime effectiveEndTime,
        LocalDateTime now
    ) {
        if (!"1".equals(deleteFlag) || !"active".equals(status)) {
            return false;
        }
        if (effectiveStartTime != null && effectiveStartTime.isAfter(now)) {
            return false;
        }
        if (effectiveEndTime != null && effectiveEndTime.isBefore(now)) {
            return false;
        }
        return true;
    }

    private void createNicknameAuditLog(
        String scene,
        String rawNickname,
        NicknameGuardHit guardHit,
        String userId,
        String traceId,
        String clientIp,
        String userAgent,
        String appVersion
    ) {
        NicknameAuditLog row = new NicknameAuditLog();
        row.setTraceId(traceId);
        row.setUserId(userId);
        row.setScene(scene);
        row.setRawNickname(CharSequenceUtil.nullToEmpty(rawNickname));
        row.setNormalizedNickname(guardHit.normalizedNickname());
        row.setDecision(guardHit.decision());
        row.setHitSource(guardHit.hitSource());
        row.setHitRuleId(guardHit.hitRuleId());
        row.setHitPatternId(guardHit.hitPatternId());
        row.setHitGroupCode(guardHit.hitGroupCode());
        row.setHitContent(guardHit.hitContent());
        row.setMessage(guardHit.message());
        row.setClientIp(clientIp);
        row.setUserAgent(userAgent);
        row.setAppVersion(appVersion);
        row.setRuleVersionBatch(getCurrentNicknameRuleBatch());
        row.setExtraJson(null);
        row.setCreateTime(nowUtc());
        row.setUpdateTime(row.getCreateTime());
        row.setDeleteFlag("1");
        nicknameAuditLogMapper.insert(row);
    }

    private String getCurrentNicknameRuleBatch() {
        NicknameRulePublishLog row = nicknameRulePublishLogMapper.findLatestActive();
        return row == null ? null : row.getPublishBatchNo();
    }

    private Map<String, Object> buildStatusPayload(Integer id, String status, LocalDateTime updateTime) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("status", status);
        payload.put("update_time", updateTime);
        return payload;
    }

    private String normalizeRuleWord(String word) {
        return normalizeNicknameForCheck(word);
    }

    private String normalizeNicknameForCheck(String nickname) {
        String value = Normalizer.normalize(CharSequenceUtil.nullToEmpty(nickname), Normalizer.Form.NFKC)
            .trim()
            .toLowerCase(Locale.ROOT);
        value = ZERO_WIDTH_PATTERN.matcher(value).replaceAll("");
        value = SEPARATOR_PATTERN.matcher(value).replaceAll("");
        return value;
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Object parseJson(String text) {
        if (CharSequenceUtil.isBlank(text)) {
            return null;
        }
        return JSONUtil.parse(text);
    }

    private String requiredTrimmed(String value, String message) {
        String normalized = CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(value));
        if (CharSequenceUtil.isBlank(normalized)) {
            throw badRequest(message);
        }
        return normalized;
    }

    private String defaultTrimmed(String value, String defaultValue) {
        String normalized = CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(value));
        return CharSequenceUtil.isBlank(normalized) ? defaultValue : normalized;
    }

    private String trimToNull(String value) {
        String normalized = CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(value));
        return CharSequenceUtil.isBlank(normalized) ? null : normalized;
    }

    private String blankToNull(String value) {
        return trimToNull(value);
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private record NicknameCheckResult(boolean available, String message) {
    }

    private record NicknameWordRuleRuntime(
        int ruleId,
        String groupCode,
        String groupType,
        String word,
        String normalizedWord,
        String matchType,
        String decision,
        int priority,
        String riskLevel
    ) {
    }

    private record NicknameContactPatternRuntime(
        int patternId,
        String groupCode,
        String patternName,
        String patternType,
        String patternRegex,
        String decision,
        int priority,
        String riskLevel
    ) {
    }

    private record NicknameGuardHit(
        String decision,
        String message,
        String normalizedNickname,
        String hitSource,
        Integer hitRuleId,
        Integer hitPatternId,
        String hitGroupCode,
        String hitContent
    ) {
        boolean passed() {
            return "pass".equals(decision);
        }
    }
}
