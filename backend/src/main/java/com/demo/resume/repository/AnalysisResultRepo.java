package com.demo.resume.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.resume.model.entity.AnalysisResult;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnalysisResultRepo extends BaseMapper<AnalysisResult> {
}
