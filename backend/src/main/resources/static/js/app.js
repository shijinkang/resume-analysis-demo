const API_BASE = 'http://localhost:8080/api';

const VIEW_HASH = {
    workspace: '#workspace',
    'jd-manage': '#jd',
    'resume-manage': '#resume',
    analysis: '#analysis'
};

function app() {
    let pendingJdFile = null;
    let pendingResumeFiles = [];

    return {
        activeView: 'workspace',
        loading: false,
        loadingMsg: '',
        errorMsg: '',
        successMsg: '',

        jdList: [],
        resumeList: [],
        selectedJdId: null,
        checkedResumeIds: [],

        activeJdId: null,
        activeResumeId: null,
        jdDetail: null,
        resumeDetail: null,
        editJdDraft: { title: '', content: '' },
        editResumeDraft: { rawText: '' },
        detailTab: 'preview',
        isDirty: false,
        detailLoading: false,
        jdSearchQuery: '',
        resumeSearchQuery: '',

        jdFileName: '',
        jdFileSize: 0,
        pendingResumes: [],
        isDraggingJd: false,
        isDraggingResume: false,
        uploadingJd: false,
        uploadingResume: false,

        matchResults: [],
        selectedResume: null,
        questions: [],
        followups: [],
        activeTab: 'questions',
        loadingQuestions: false,

        async init() {
            this.syncViewFromHash();
            window.addEventListener('hashchange', () => this.syncViewFromHash());
            await Promise.all([
                this.loadJdList(),
                this.loadResumeList()
            ]);
        },

        syncViewFromHash() {
            const hash = location.hash.slice(1) || 'workspace';
            const map = { workspace: 'workspace', jd: 'jd-manage', resume: 'resume-manage', analysis: 'analysis' };
            const view = map[hash] || 'workspace';
            if (view === 'analysis' && this.matchResults.length === 0) {
                this.activeView = 'workspace';
                this.syncHash('workspace');
                return;
            }
            this.activeView = view;
        },

        syncHash(view) {
            const hash = VIEW_HASH[view] || '#workspace';
            if (location.hash !== hash) {
                location.hash = hash.slice(1);
            }
        },

        async navigate(view) {
            if (this.isDirty && !confirm('有未保存的修改，确定离开吗？')) {
                return;
            }
            this.isDirty = false;
            this.activeView = view;
            this.syncHash(view);
            if (view === 'jd-manage' && !this.activeJdId && this.jdList.length > 0) {
                await this.loadJdDetail(this.jdList[0].id);
                this.activeJdId = this.jdList[0].id;
                this.detailTab = 'preview';
            }
            if (view === 'resume-manage' && !this.activeResumeId && this.resumeList.length > 0) {
                await this.loadResumeDetail(this.resumeList[0].id);
                this.activeResumeId = this.resumeList[0].id;
                this.detailTab = 'preview';
            }
            if (view === 'analysis' && this.matchResults.length > 0 && !this.selectedResume) {
                this.selectResume(this.matchResults[0]);
            }
        },

        enterJdManage(id) {
            this.isDirty = false;
            this.activeView = 'jd-manage';
            this.syncHash('jd-manage');
            if (id) {
                this.selectJdInManage(id);
            } else if (this.jdList.length > 0 && !this.activeJdId) {
                this.selectJdInManage(this.jdList[0].id);
            }
        },

        enterResumeManage(id) {
            this.isDirty = false;
            this.activeView = 'resume-manage';
            this.syncHash('resume-manage');
            if (id) {
                this.selectResumeInManage(id);
            } else if (this.resumeList.length > 0 && !this.activeResumeId) {
                this.selectResumeInManage(this.resumeList[0].id);
            }
        },

        get filteredJdList() {
            const q = this.jdSearchQuery.trim().toLowerCase();
            if (!q) return this.jdList;
            return this.jdList.filter(jd => jd.title.toLowerCase().includes(q));
        },

        get filteredResumeList() {
            const q = this.resumeSearchQuery.trim().toLowerCase();
            if (!q) return this.resumeList;
            return this.resumeList.filter(r => r.fileName.toLowerCase().includes(q));
        },

        get workspaceJdList() {
            return this.jdList.slice(0, 5);
        },

        get selectedJdTitle() {
            const jd = this.jdList.find(j => j.id === this.selectedJdId);
            return jd ? jd.title : '';
        },

        async loadJdList() {
            try {
                const res = await fetch(`${API_BASE}/upload/jd/list`);
                if (res.ok) {
                    this.jdList = await res.json();
                    if (this.selectedJdId && !this.jdList.find(j => j.id === this.selectedJdId)) {
                        this.selectedJdId = null;
                    }
                }
            } catch (error) {
                console.error('加载 JD 列表失败:', error);
            }
        },

        async loadResumeList() {
            try {
                const res = await fetch(`${API_BASE}/upload/resumes/list`);
                if (res.ok) {
                    this.resumeList = await res.json();
                    this.checkedResumeIds = this.checkedResumeIds.filter(id =>
                        this.resumeList.some(r => r.id === id)
                    );
                }
            } catch (error) {
                console.error('加载简历列表失败:', error);
            }
        },

        selectJd(id) {
            this.selectedJdId = id;
        },

        toggleResume(id) {
            const index = this.checkedResumeIds.indexOf(id);
            if (index > -1) {
                this.checkedResumeIds.splice(index, 1);
            } else {
                this.checkedResumeIds.push(id);
            }
        },

        isResumeChecked(id) {
            return this.checkedResumeIds.indexOf(id) > -1;
        },

        toggleAllResumes() {
            if (this.checkedResumeIds.length === this.resumeList.length && this.resumeList.length > 0) {
                this.checkedResumeIds = [];
            } else {
                this.checkedResumeIds = this.resumeList.map(r => r.id);
            }
        },

        async selectJdInManage(id) {
            if (this.isDirty && !confirm('有未保存的修改，确定切换吗？')) {
                return;
            }
            this.activeJdId = id;
            this.detailTab = 'preview';
            this.isDirty = false;
            await this.loadJdDetail(id);
        },

        async selectResumeInManage(id) {
            if (this.isDirty && !confirm('有未保存的修改，确定切换吗？')) {
                return;
            }
            this.activeResumeId = id;
            this.detailTab = 'preview';
            this.isDirty = false;
            await this.loadResumeDetail(id);
        },

        switchDetailTab(tab) {
            if (this.isDirty && tab === 'preview' && !confirm('有未保存的修改，确定切换吗？')) {
                return;
            }
            this.detailTab = tab;
        },

        markJdDirty() {
            this.isDirty = true;
        },

        markResumeDirty() {
            this.isDirty = true;
        },

        async loadJdDetail(id) {
            this.detailLoading = true;
            try {
                const res = await fetch(`${API_BASE}/upload/jd/${id}`);
                if (!res.ok) throw new Error('加载 JD 详情失败');
                this.jdDetail = await res.json();
                this.editJdDraft = {
                    title: this.jdDetail.title || '',
                    content: this.jdDetail.content || ''
                };
            } catch (error) {
                this.showError(error.message);
                this.jdDetail = null;
            } finally {
                this.detailLoading = false;
            }
        },

        async loadResumeDetail(id) {
            this.detailLoading = true;
            try {
                const res = await fetch(`${API_BASE}/upload/resumes/${id}`);
                if (!res.ok) throw new Error('加载简历详情失败');
                this.resumeDetail = await res.json();
                this.editResumeDraft = {
                    rawText: this.resumeDetail.rawText || ''
                };
            } catch (error) {
                this.showError(error.message);
                this.resumeDetail = null;
            } finally {
                this.detailLoading = false;
            }
        },

        cancelJdEdit() {
            if (this.jdDetail) {
                this.editJdDraft = {
                    title: this.jdDetail.title || '',
                    content: this.jdDetail.content || ''
                };
            }
            this.isDirty = false;
            this.detailTab = 'preview';
        },

        cancelResumeEdit() {
            if (this.resumeDetail) {
                this.editResumeDraft = {
                    rawText: this.resumeDetail.rawText || ''
                };
            }
            this.isDirty = false;
            this.detailTab = 'preview';
        },

        async saveJd() {
            if (!this.activeJdId) return;
            if (!this.editJdDraft.content?.trim()) {
                this.showError('JD 内容不能为空');
                return;
            }
            this.detailLoading = true;
            try {
                const res = await fetch(`${API_BASE}/upload/jd/${this.activeJdId}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        title: this.editJdDraft.title,
                        content: this.editJdDraft.content
                    })
                });
                if (!res.ok) {
                    const err = await res.json().catch(() => ({}));
                    throw new Error(err.message || '保存失败');
                }
                this.jdDetail = await res.json();
                this.editJdDraft = {
                    title: this.jdDetail.title,
                    content: this.jdDetail.content
                };
                this.isDirty = false;
                this.detailTab = 'preview';
                await this.loadJdList();
                this.showSuccess('JD 已保存');
            } catch (error) {
                this.showError(error.message || '保存失败');
            } finally {
                this.detailLoading = false;
            }
        },

        async saveResume() {
            if (!this.activeResumeId) return;
            if (!this.editResumeDraft.rawText?.trim()) {
                this.showError('简历内容不能为空');
                return;
            }
            this.detailLoading = true;
            try {
                const res = await fetch(`${API_BASE}/upload/resumes/${this.activeResumeId}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ rawText: this.editResumeDraft.rawText })
                });
                if (!res.ok) {
                    const err = await res.json().catch(() => ({}));
                    throw new Error(err.message || '保存失败');
                }
                this.resumeDetail = await res.json();
                this.editResumeDraft = { rawText: this.resumeDetail.rawText };
                this.isDirty = false;
                this.detailTab = 'preview';
                await this.loadResumeList();
                this.showSuccess('简历已保存，建议重新分析以获取最新匹配结果');
            } catch (error) {
                this.showError(error.message || '保存失败');
            } finally {
                this.detailLoading = false;
            }
        },

        async deleteJd() {
            if (!this.activeJdId) return;
            if (!confirm('确定删除此 JD 吗？此操作不可恢复。')) return;
            try {
                const res = await fetch(`${API_BASE}/upload/jd/${this.activeJdId}`, { method: 'DELETE' });
                if (!res.ok) throw new Error('删除失败');
                if (this.selectedJdId === this.activeJdId) {
                    this.selectedJdId = null;
                }
                this.activeJdId = null;
                this.jdDetail = null;
                this.isDirty = false;
                await this.loadJdList();
                this.showSuccess('JD 已删除');
            } catch (error) {
                this.showError(error.message || '删除失败');
            }
        },

        async deleteResume() {
            if (!this.activeResumeId) return;
            if (!confirm('确定删除此简历吗？此操作不可恢复。')) return;
            try {
                const res = await fetch(`${API_BASE}/upload/resumes/${this.activeResumeId}`, { method: 'DELETE' });
                if (!res.ok) throw new Error('删除失败');
                const deletedId = this.activeResumeId;
                this.checkedResumeIds = this.checkedResumeIds.filter(id => id !== deletedId);
                this.activeResumeId = null;
                this.resumeDetail = null;
                this.isDirty = false;
                await this.loadResumeList();
                this.showSuccess('简历已删除');
            } catch (error) {
                this.showError(error.message || '删除失败');
            }
        },

        clearJdFile() {
            pendingJdFile = null;
            this.jdFileName = '';
            this.jdFileSize = 0;
            if (this.$refs.jdInput) this.$refs.jdInput.value = '';
        },

        clearResumeFiles() {
            pendingResumeFiles = [];
            this.pendingResumes = [];
            if (this.$refs.resumeInput) this.$refs.resumeInput.value = '';
        },

        syncJdInputFile(file) {
            if (!file || !this.$refs.jdInput) return;
            try {
                const dt = new DataTransfer();
                dt.items.add(file);
                this.$refs.jdInput.files = dt.files;
            } catch (e) {
                console.warn('无法同步文件到 input:', e);
            }
        },

        async uploadSelectedJd() {
            const file = pendingJdFile || this.$refs.jdInput?.files?.[0];
            if (!file || this.uploadingJd) {
                if (!file) this.showError('请先选择 JD 文件');
                return;
            }

            this.uploadingJd = true;
            this.errorMsg = '';

            try {
                const formData = new FormData();
                formData.append('file', file);
                const res = await fetch(`${API_BASE}/upload/jd`, { method: 'POST', body: formData });

                if (!res.ok) {
                    const err = await res.json().catch(() => ({}));
                    throw new Error(err.message || 'JD 上传失败');
                }

                const data = await res.json();
                await this.loadJdList();
                this.selectJd(data.id);
                if (this.activeView === 'jd-manage') {
                    await this.selectJdInManage(data.id);
                }
                this.showSuccess('JD 上传成功！');
                this.clearJdFile();
            } catch (error) {
                this.showError(error.message || 'JD 上传失败');
            } finally {
                this.uploadingJd = false;
            }
        },

        async uploadSelectedResumes() {
            if (pendingResumeFiles.length === 0 || this.uploadingResume) {
                if (pendingResumeFiles.length === 0) this.showError('请先选择简历文件');
                return;
            }

            this.uploadingResume = true;
            this.errorMsg = '';

            try {
                const formData = new FormData();
                pendingResumeFiles.forEach(file => formData.append('files', file));
                const res = await fetch(`${API_BASE}/upload/resumes`, { method: 'POST', body: formData });

                if (!res.ok) throw new Error('简历上传失败');

                const results = await res.json();
                const successIds = results.filter(r => r.status === 'SUCCESS').map(r => r.id);

                await this.loadResumeList();

                successIds.forEach(id => {
                    if (this.checkedResumeIds.indexOf(id) === -1) {
                        this.checkedResumeIds.push(id);
                    }
                });

                if (this.activeView === 'resume-manage' && successIds.length > 0) {
                    await this.selectResumeInManage(successIds[0]);
                }

                this.showSuccess(`成功上传 ${successIds.length} 份简历！`);
                this.clearResumeFiles();
            } catch (error) {
                this.showError(error.message || '简历上传失败');
            } finally {
                this.uploadingResume = false;
            }
        },

        showSuccess(msg) {
            this.successMsg = msg;
            setTimeout(() => this.successMsg = '', 4000);
        },

        get canStart() {
            return this.selectedJdId !== null && this.checkedResumeIds.length > 0 && !this.loading;
        },

        get recommendColor() {
            return (rec) => {
                if (rec === 'PROCEED') return 'text-green-600 bg-green-50 border-green-200';
                if (rec === 'REJECT') return 'text-red-600 bg-red-50 border-red-200';
                return 'text-yellow-600 bg-yellow-50 border-yellow-200';
            };
        },

        get recommendLabel() {
            return (rec) => {
                if (rec === 'PROCEED') return '推荐';
                if (rec === 'REJECT') return '不推荐';
                return '待定';
            };
        },

        get scoreColor() {
            return (score) => {
                if (score >= 80) return 'text-green-600';
                if (score >= 60) return 'text-yellow-500';
                return 'text-red-500';
            };
        },

        get scoreBarColor() {
            return (score) => {
                if (score >= 80) return 'bg-green-500';
                if (score >= 60) return 'bg-yellow-400';
                return 'bg-red-400';
            };
        },

        get difficultyLabel() {
            return (d) => ['', '入门', '基础', '中级', '高级', '专家'][d] || d;
        },

        get categoryLabel() {
            return (c) => {
                const map = { TECHNICAL: '技术', BEHAVIORAL: '行为', SCENARIO: '场景' };
                return map[c] || c;
            };
        },

        get categoryColor() {
            return (c) => {
                const map = {
                    TECHNICAL: 'bg-blue-100 text-blue-700',
                    BEHAVIORAL: 'bg-purple-100 text-purple-700',
                    SCENARIO: 'bg-orange-100 text-orange-700'
                };
                return map[c] || 'bg-gray-100 text-gray-700';
            };
        },

        handleJdFile(event) {
            const file = event.target.files[0];
            if (!file) {
                this.clearJdFile();
                return;
            }
            if (!this.isValidFile(file)) {
                this.showError('仅支持 PDF 或 Word (.docx) 文件');
                event.target.value = '';
                this.clearJdFile();
                return;
            }
            pendingJdFile = file;
            this.jdFileName = file.name;
            this.jdFileSize = file.size;
        },

        handleJdDrop(event) {
            this.isDraggingJd = false;
            const file = event.dataTransfer.files[0];
            if (file && this.isValidFile(file)) {
                pendingJdFile = file;
                this.jdFileName = file.name;
                this.jdFileSize = file.size;
                this.syncJdInputFile(file);
            } else if (file) {
                this.showError('仅支持 PDF 或 Word (.docx) 文件');
            }
        },

        handleResumeFiles(event) {
            const newFiles = Array.from(event.target.files).filter(f => this.isValidFile(f));
            if (newFiles.length === 0 && event.target.files.length > 0) {
                this.showError('仅支持 PDF 或 Word (.docx) 文件');
            }
            newFiles.forEach(f => {
                pendingResumeFiles.push(f);
                this.pendingResumes.push({ name: f.name, size: f.size });
            });
        },

        handleResumeDrop(event) {
            this.isDraggingResume = false;
            const newFiles = Array.from(event.dataTransfer.files).filter(f => this.isValidFile(f));
            if (newFiles.length === 0 && event.dataTransfer.files.length > 0) {
                this.showError('仅支持 PDF 或 Word (.docx) 文件');
            }
            newFiles.forEach(f => {
                pendingResumeFiles.push(f);
                this.pendingResumes.push({ name: f.name, size: f.size });
            });
        },

        isValidFile(file) {
            return file.name.endsWith('.pdf') || file.name.endsWith('.docx');
        },

        removeResume(index) {
            pendingResumeFiles.splice(index, 1);
            this.pendingResumes.splice(index, 1);
        },

        formatFileSize(bytes) {
            if (bytes < 1024) return bytes + ' B';
            if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
            return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
        },

        formatDate(dateStr) {
            if (!dateStr) return '';
            const date = new Date(dateStr);
            const now = new Date();
            const diff = now - date;
            const days = Math.floor(diff / (1000 * 60 * 60 * 24));

            if (days === 0) return '今天';
            if (days === 1) return '昨天';
            if (days < 7) return `${days}天前`;
            return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
        },

        formatDateTime(dateStr) {
            if (!dateStr) return '';
            return new Date(dateStr).toLocaleString('zh-CN', {
                year: 'numeric', month: 'short', day: 'numeric',
                hour: '2-digit', minute: '2-digit'
            });
        },

        async startAnalysis() {
            if (!this.selectedJdId || this.checkedResumeIds.length === 0) {
                this.showError('请选择一个 JD 和至少一份简历');
                return;
            }

            this.loading = true;
            this.errorMsg = '';

            try {
                this.loadingMsg = 'AI 正在分析匹配度，请稍候...';
                await this.performMatching();
                this.activeView = 'analysis';
                this.syncHash('analysis');
                if (this.matchResults.length > 0) {
                    await this.selectResume(this.matchResults[0]);
                }
            } catch (error) {
                this.showError(error.message || '分析失败，请重试');
            } finally {
                this.loading = false;
                this.loadingMsg = '';
            }
        },

        async performMatching() {
            const res = await fetch(`${API_BASE}/analysis/match`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ jdId: this.selectedJdId, resumeIds: this.checkedResumeIds })
            });
            if (!res.ok) throw new Error('匹配分析失败');
            const results = await res.json();
            this.matchResults = results.sort((a, b) => (b.score || 0) - (a.score || 0));
        },

        async selectResume(result) {
            this.selectedResume = result;
            this.activeTab = 'questions';
            this.questions = [];
            this.followups = [];
            this.loadingQuestions = true;
            try {
                await Promise.all([
                    this.loadQuestions(result.resumeId),
                    this.loadFollowups(result.resumeId)
                ]);
            } finally {
                this.loadingQuestions = false;
            }
        },

        async loadQuestions(resumeId) {
            const res = await fetch(`${API_BASE}/interview/questions/${resumeId}`, { method: 'POST' });
            if (res.ok) {
                const data = await res.json();
                this.questions = data.questions || [];
            }
        },

        async loadFollowups(resumeId) {
            const res = await fetch(`${API_BASE}/interview/followup/${resumeId}`, { method: 'POST' });
            if (res.ok) {
                const data = await res.json();
                this.followups = data.followUpQuestions || data.questions || [];
            }
        },

        showError(msg) {
            this.errorMsg = msg;
            setTimeout(() => this.errorMsg = '', 6000);
        },

        reset() {
            this.activeView = 'workspace';
            this.syncHash('workspace');
            this.selectedJdId = null;
            this.checkedResumeIds = [];
            this.matchResults = [];
            this.selectedResume = null;
            this.questions = [];
            this.followups = [];
            this.errorMsg = '';
            this.successMsg = '';
            this.loadingMsg = '';
        }
    };
}
