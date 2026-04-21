package com.earthseaedu.backend.service;

import com.earthseaedu.backend.dto.auth.AuthResponses;
import com.earthseaedu.backend.model.nickname.NicknameAuditLog;
import com.earthseaedu.backend.model.nickname.NicknameContactPattern;
import com.earthseaedu.backend.model.nickname.NicknameRuleGroup;
import com.earthseaedu.backend.model.nickname.NicknameWordRule;
import com.earthseaedu.backend.support.PageResult;
import java.util.List;
import java.util.Map;

/**
 * 昵称风控服务，负责昵称可用性校验、规则维护和审计记录查询。
 */
public interface NicknameGuardService {

    /**
     * 校验昵称是否可用，并返回命中规则和风险判定。
     *
     * @param userId 用户 ID
     * @param nickname 昵称或微信昵称
     * @param clientIp 客户端 IP
     * @param userAgent 客户端 User-Agent
     * @return 处理后的响应对象。
     */
    AuthResponses.AvailabilityCheckResponse checkNicknameAvailability(
        String userId,
        String nickname,
        String clientIp,
        String userAgent
    );

    /**
     * 为用户更新昵称，并记录风控审计信息。
     *
     * @param userId 用户 ID
     * @param nickname 昵称或微信昵称
     * @param clientIp 客户端 IP
     * @param userAgent 客户端 User-Agent
     * @return 处理后的响应对象。
     */
    AuthResponses.NicknameResponse updateNicknameForUser(
        String userId,
        String nickname,
        String clientIp,
        String userAgent
    );

    /**
     * 按状态和分组类型查询昵称规则组。
     *
     * @param status 状态筛选或目标状态
     * @param groupType 规则组类型
     * @return 列表结果。
     */
    List<NicknameRuleGroup> listNicknameRuleGroups(String status, String groupType);

    /**
     * 创建昵称风控规则组。
     *
     * @param groupCode 规则组编码
     * @param groupName 规则组名称
     * @param groupType 规则组类型
     * @param scope 规则适用范围
     * @param status 状态筛选或目标状态
     * @param priority 优先级
     * @param description 描述
     * @return 处理后的响应对象。
     */
    NicknameRuleGroup createNicknameRuleGroup(
        String groupCode,
        String groupName,
        String groupType,
        String scope,
        String status,
        Integer priority,
        String description
    );

    /**
     * 创建昵称敏感词规则。
     *
     * @param groupId 规则组 ID
     * @param word 敏感词
     * @param matchType 匹配方式
     * @param decision 判定结果
     * @param status 状态筛选或目标状态
     * @param priority 优先级
     * @param riskLevel 风险等级
     * @param source 来源
     * @param note 备注
     * @return 处理后的响应对象。
     */
    NicknameWordRule createNicknameWordRule(
        Integer groupId,
        String word,
        String matchType,
        String decision,
        String status,
        Integer priority,
        String riskLevel,
        String source,
        String note
    );

    /**
     * 按筛选条件查询昵称敏感词规则。
     *
     * @param groupId 规则组 ID
     * @param status 状态筛选或目标状态
     * @param decision 判定结果
     * @param keyword 检索关键字
     * @param limit 最大返回条数
     * @return 分页结果。
     */
    PageResult<NicknameWordRule> listNicknameWordRules(
        Integer groupId,
        String status,
        String decision,
        String keyword,
        int limit
    );

    /**
     * 创建昵称联系方式识别规则。
     *
     * @param patternName 规则名称
     * @param patternType 规则类型
     * @param patternRegex 匹配正则
     * @param groupId 规则组 ID
     * @param decision 判定结果
     * @param status 状态筛选或目标状态
     * @param priority 优先级
     * @param riskLevel 风险等级
     * @param normalizedHint 归一化提示
     * @param note 备注
     * @return 处理后的响应对象。
     */
    NicknameContactPattern createNicknameContactPattern(
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
    );

    /**
     * 按筛选条件查询昵称联系方式识别规则。
     *
     * @param groupId 规则组 ID
     * @param status 状态筛选或目标状态
     * @param patternType 规则类型
     * @param keyword 检索关键字
     * @param limit 最大返回条数
     * @return 分页结果。
     */
    PageResult<NicknameContactPattern> listNicknameContactPatterns(
        Integer groupId,
        String status,
        String patternType,
        String keyword,
        int limit
    );

    /**
     * 更新昵称风控规则对象的状态。
     *
     * @param targetType 目标对象类型
     * @param targetId 目标对象 ID
     * @param status 状态筛选或目标状态
     * @return 处理后的响应对象。
     */
    Map<String, Object> updateNicknameRuleTargetStatus(String targetType, Integer targetId, String status);

    /**
     * 按筛选条件查询昵称风控审计日志。
     *
     * @param decision 判定结果
     * @param scene 风控触发场景
     * @param hitGroupCode 命中的规则组编码
     * @param limit 最大返回条数
     * @return 分页结果。
     */
    PageResult<NicknameAuditLog> listNicknameAuditLogs(
        String decision,
        String scene,
        String hitGroupCode,
        int limit
    );

    /**
     * 将昵称规则组转换为接口响应结构。
     *
     * @param row 待转换的数据行
     * @return 处理后的响应对象。
     */
    Map<String, Object> toRuleGroupPayload(NicknameRuleGroup row);

    /**
     * 将昵称敏感词规则转换为接口响应结构。
     *
     * @param row 待转换的数据行
     * @return 处理后的响应对象。
     */
    Map<String, Object> toWordRulePayload(NicknameWordRule row);

    /**
     * 将联系方式识别规则转换为接口响应结构。
     *
     * @param row 待转换的数据行
     * @return 处理后的响应对象。
     */
    Map<String, Object> toContactPatternPayload(NicknameContactPattern row);

    /**
     * 将昵称审计日志转换为接口响应结构。
     *
     * @param row 待转换的数据行
     * @return 处理后的响应对象。
     */
    Map<String, Object> toAuditLogPayload(NicknameAuditLog row);
}
