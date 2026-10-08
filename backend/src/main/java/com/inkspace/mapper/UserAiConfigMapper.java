package com.inkspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inkspace.domain.entity.UserAiConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 AI 配置 Mapper。
 */
@Mapper
public interface UserAiConfigMapper extends BaseMapper<UserAiConfig> {
}
