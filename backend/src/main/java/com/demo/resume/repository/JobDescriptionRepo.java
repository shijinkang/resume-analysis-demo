package com.demo.resume.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.resume.model.entity.JobDescription;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobDescriptionRepo extends BaseMapper<JobDescription> {
}
