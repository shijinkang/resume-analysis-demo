package com.demo.resume.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.resume.model.entity.Resume;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ResumeRepo extends BaseMapper<Resume> {
}
