const releases = [
  {version:'2.2.36', title:'流水线阶段状态统一', current:true, items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.35', title:'运行配置项收敛', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.34', title:'验证脚本与持续集成精简', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.33', title:'前端模块加载链路精简', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.32', title:'目标机真实性能证据门禁', items:["真实 CUDA OOM 后恢复计算，不再接受任意 marker 命令","至少 60 分钟重复真实 FFmpeg 音视频转码","schema v3 校验 runner、场景实现和原始日志哈希"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.31', title:'PostgreSQL 2.2.4 基线升级门禁', items:["真实 PostgreSQL 16 从 B39 基线升级到当前版本","完整历史和基线升级均执行 Flyway validate","比较表列、约束和索引签名，阻止结构漂移"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.30', title:'长视频发布性能门禁', items:["五分钟 1080p FFmpeg 转码改为每次 CI 必跑","同提交发布证据新增长视频性能必过字段","目标机 GPU OOM 和 60 分钟长跑仍保持独立验收"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.29', title:'Android 自动化验收闭环', items:["API 29/35 平台导入协议回放门禁通过","2.2.4 持久化 AVD 覆盖升级与数据保留通过","生产签名和 OEM 真机兼容继续独立标记为未验证"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.28', title:'PostgreSQL 生产路径验证增强', items:["真实 PostgreSQL 16 执行完整迁移","验证租户隔离、外键与乐观锁","发布证据强制要求 PostgreSQL 门槛通过"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.27', title:'真实媒体测试与发布证据增强', items:["真实媒体输出测试缺少 FFmpeg 时明确失败","新增性能套件规则防止静默跳过逻辑回流","同步 Web、Windows 与 Android 版本至 2.2.27"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.26', title:'界面操作体验优化', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.25', title:'任务终止闭环与 Android 实证增强', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.24', title:'稳定性与功能更新', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.23', title:'稳定性与功能更新', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.22', title:'稳定性与功能更新', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.21', title:'稳定性与功能更新', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.20', title:'稳定性与功能更新', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.19', title:'稳定性与功能更新', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.18', title:'Meme 叠加像素级验收', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.17', title:'渲染占用文件真实恢复', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.16', title:'转场真实输出强验证', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.15', title:'同提交 CI 发布证据', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.14', title:'发布证据语义校验', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.13', title:'多源证据文案评分', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.12', title:'证据约束脚本评分', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.11', title:'发布就绪度看板', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.10', title:'发布性能专项门禁', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.9', title:'Android 正式发行门禁', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.8', title:'平台下载断点恢复', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.7', title:'公共素材下载闭环', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.6', title:'Android LUT 调色闭环', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.5', title:'动态事件采样与说话人分段', items:["同步更新 Web、Windows 与 Android 版本","完成本版本功能修复与稳定性检查","创建并同步 GitHub、Gitee 发布标签"], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.4', title:'后台管理中心增强', items:['新增运营总览、AI 趋势、状态分布和近期审计','完善用户、项目、云同步、存储的搜索筛选、分页与 CSV 导出','修复存储检查和清理接口缺少管理员权限校验的问题'], jump:{view:'studio', selector:'#updates-open'}},
  {version:'2.2.3', title:'版本公告中文化修复', items:['修复 Windows PowerShell 解析 UTF-8 中文模板时产生乱码的问题','将近期 Web 与 Android 英文公告恢复为对应功能的中文内容','后续自动升级默认生成中文发布信息'], jump:{view:'studio', selector:'#updates-open'}},
  {version:'2.2.2', title:'使用引导自动跳转修复', items:['引导步骤自动切换到对应功能页面','等待异步功能加载完成后再滚动并高亮目标','目标暂不可见时保留当前步骤并支持重试'], jump:{view:'studio', selector:'#guide-open'}},
  {version:'2.2.1', title:'权限隔离与导演评审修复', items:['完善账号与项目权限隔离','修复 AI 导演评审流程中的权限与数据问题','同步 Web、Windows 与 Android 发布版本'], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.2.0', title:'AI 导演评审会', items:['新增多角色 AI 导演评审流程','保存评审发言、共识和最终剪辑决策','支持用户确认后应用到时间线'], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.1.8', title:'本地账号与后台管理中心', items:['新增注册、登录与匿名使用模式','新增用户、项目和云端 API 管理能力','实现客户端与后台账号数据互通'], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.1.7', title:'社区生态与同源多版本', items:['分享和安装游戏知识包、剪辑规则与风格配置','同一录像生成剧情版、攻略版、搞笑版和复盘版独立任务','四种版本复用源视频，避免重复占用超大文件空间','导出 JSON 与 Markdown 剪辑决策报告用于二次修改和毕业设计展示'], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.1.6', title:'战局导演与超大视频存储', items:['按已确认事件生成五幕战局叙事并自动调整镜头、解说和音乐','新增知识包导入导出、个人导演档案与事实一致性检查','支持 40GB 级视频磁盘流式上传、容量预检和后台存储管理','将 DeepSeek 上下文改为结构化索引与按模块分卷'], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.1.5', title:'并发安全与可解释事件时间线', items:['统一更新 Web、Windows 与 Android 版本','修复工程修订并发覆盖风险','自动创建并同步 GitHub、Gitee 发布标签'], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.1.4', title:'修复展示轻量版无法启动', items:['改用 PowerShell 启动器，避免批处理换行兼容问题','启动输出与错误写入独立日志','已使用打包目录完成真实启动健康检查'], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.1.3', title:'修复 NVENC 驱动不兼容导致音画合成失败', items:['动态字幕音画合成沿用当前有效编码器','NVENC 初始化失败时自动回退 libx264 CPU 编码','清理失败的半成品后自动重试，无需重新剪辑'], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.1.2', title:'修复安装包误装旧程序', items:['发行构建先清理历史 JAR','只打包与当前版本精确匹配的 JAR','创建安装器前校验内置前端版本，拒绝陈旧产物'], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.1.1', title:'Windows 发行构建稳定性', items:['发行构建自动识别并关闭当前项目的 Vite/esbuild 文件锁','不影响其他 Node 程序','保留可复现的 npm ci 依赖恢复'], jump:{view:'studio', selector:'#task-list'}},
  {version:'2.1.0', title:'更新公告与功能定位', items:['新增完整历史更新公告','支持跳转到相关视图并高亮功能位置','本机记录已读状态，不上传浏览记录'], jump:{view:'studio', selector:'#task-list', note:'请选择一个已生成任务进入分镜工作台，可查看 2.0 版本树。'}},
  {version:'2.0.0', title:'完整工程版本树', items:['版本命名与父子分支可视化','任意历史节点切换','切换后同步分镜并使旧渲染失效'], jump:{view:'studio', selector:'.revision-tree-panel', note:'请先打开一个已生成任务的分镜工作台。'}},
  {version:'1.8.0', title:'前端按需加载', items:['素材库和平台导入按视图加载','诊断模块首次打开时加载','降低首屏脚本解析与内存压力'], jump:{view:'assets', selector:'.asset-library'}},
  {version:'1.7.0', title:'片段连接与结构撤回', items:['剪断片段可重新连接','连接保留文案、字幕、特效和素材','结构编辑支持持久化撤回与恢复'], jump:{view:'studio', selector:'[data-open-storyboard]', note:'请选择一个已生成分镜的任务。'}},
  {version:'1.6.0–1.6.3', title:'无 AI 可编辑工程', items:['无 AI 模式生成基础可编辑时间线','转写、自动素材和特效可随时独立调用','允许纯原声、仅字幕和纯画面剪辑'], jump:{view:'studio', selector:'.enhancement-toolbox', note:'请选择一个准备完成的任务查看智能增强工具箱。'}},
  {version:'1.5.x', title:'性能、清理与渲染稳定性', items:['安全存储清理与远程封面失败缓存','渲染视频滤镜和音频混音拆分','GitHub 与 Gitee 双远端同步发布'], jump:{view:'studio', selector:'.storage-overview'}}
];

const dialog = document.querySelector('#updates-dialog');
const list = document.querySelector('#updates-list');
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));

if (dialog && list) {
  list.innerHTML = releases.map((release, index) => `<article class="release-card ${release.current ? 'current' : ''}">
    <header><b>v${escapeHtml(release.version)}</b><h3>${escapeHtml(release.title)}</h3>${release.current ? '<i>当前版本</i>' : ''}</header>
    <ul>${release.items.map(item => `<li>${escapeHtml(item)}</li>`).join('')}</ul>
    ${release.jump ? `<button type="button" data-release-jump="${index}">查看更新效果</button>` : ''}
  </article>`).join('');
  localStorage.setItem('gameNarrator.lastSeenRelease', releases[0].version);
  document.querySelector('#updates-open')?.classList.remove('has-update');
  document.querySelector('#updates-open')?.addEventListener('click', () => { if (!dialog.open) dialog.showModal(); });
  document.querySelector('#updates-close')?.addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', event => { if (event.target === dialog) dialog.close(); });
  list.addEventListener('click', event => {
    const button = event.target.closest('[data-release-jump]');
    if (!button) return;
    dialog.close();
    window.dispatchEvent(new CustomEvent('gamenarrator-release-jump', {detail:releases[Number(button.dataset.releaseJump)].jump}));
  });
}
