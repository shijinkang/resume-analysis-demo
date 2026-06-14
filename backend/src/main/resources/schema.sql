-- Job Descriptions Table
CREATE TABLE IF NOT EXISTS job_descriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    requirements TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Resumes Table
CREATE TABLE IF NOT EXISTS resumes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    raw_text TEXT,
    structured_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Analysis Results Table
CREATE TABLE IF NOT EXISTS analysis_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jd_id BIGINT,
    resume_id BIGINT,
    match_score INT CHECK (match_score >= 0 AND match_score <= 100),
    match_reason TEXT,
    match_dimensions TEXT,
    questions TEXT,
    followup_questions TEXT,
    recommendation VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_resumes_created_at ON resumes(created_at);
CREATE INDEX IF NOT EXISTS idx_analysis_results_jd_id ON analysis_results(jd_id);
CREATE INDEX IF NOT EXISTS idx_analysis_results_resume_id ON analysis_results(resume_id);
CREATE INDEX IF NOT EXISTS idx_analysis_results_score ON analysis_results(match_score);
