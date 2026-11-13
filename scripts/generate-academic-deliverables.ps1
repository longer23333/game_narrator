param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\docs\deliverables')
)

$ErrorActionPreference = 'Stop'
$output = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $output | Out-Null

$title = '多模态游戏视频智能解说与自动剪辑系统的设计与实现'
$student = '陈华熠'
$studentId = '20230862133'
$studentClass = '2023级网络工程班'
$teacher = '张标汉'
$teacherTitle = '【职称待填写】'

function Add-WordParagraph {
    param($Document, [string]$Text, [int]$Size = 12, [bool]$Bold = $false,
          [int]$Alignment = 0, [int]$FirstLine = 24, [double]$LineSpacing = 22)
    $range = $Document.Range($Document.Content.End - 1, $Document.Content.End - 1)
    $range.Text = $Text
    $range.InsertParagraphAfter()
    $p = $Document.Paragraphs.Item($Document.Paragraphs.Count - 1)
    $p.Range.Font.NameFarEast = '宋体'
    $p.Range.Font.Name = 'Times New Roman'
    $p.Range.Font.Size = $Size
    $p.Range.Font.Bold = [int]$Bold
    $p.Alignment = $Alignment
    $p.Format.FirstLineIndent = $FirstLine
    $p.Format.LineSpacingRule = 4
    $p.Format.LineSpacing = $LineSpacing
    $p.Format.SpaceAfter = 0
    return $p
}

function Add-WordHeading {
    param($Document, [string]$Text, [int]$Level)
    $size = if ($Level -eq 1) { 16 } elseif ($Level -eq 2) { 14 } else { 12 }
    $p = Add-WordParagraph $Document $Text $size $true 0 0 22
    $p.Range.Style = "标题 $Level"
    $p.Range.Font.NameFarEast = '黑体'
    $p.Range.Font.Name = 'Times New Roman'
    if ($Level -eq 1) { $p.Alignment = 1; $p.Format.SpaceBefore = 12; $p.Format.SpaceAfter = 12 }
    else { $p.Format.SpaceBefore = 6; $p.Format.SpaceAfter = 3 }
    return $p
}

function Add-WordBullet {
    param($Document, [string]$Text)
    $p = Add-WordParagraph $Document $Text 12 $false 0 0 22
    $p.Range.ListFormat.ApplyBulletDefault()
    return $p
}

function Add-PageBreak($Document) {
    $range = $Document.Range($Document.Content.End - 1, $Document.Content.End - 1)
    $range.InsertBreak(7)
}

$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    $doc = $word.Documents.Add()
    $section = $doc.Sections.Item(1)
    $section.PageSetup.TopMargin = $word.CentimetersToPoints(2.5)
    $section.PageSetup.BottomMargin = $word.CentimetersToPoints(2.5)
    $section.PageSetup.LeftMargin = $word.CentimetersToPoints(3.0)
    $section.PageSetup.RightMargin = $word.CentimetersToPoints(2.5)

    Add-WordParagraph $doc '' 12 $false 1 0 22 | Out-Null
    Add-WordParagraph $doc '2023级学年设计' 22 $true 1 0 28 | Out-Null
    1..3 | ForEach-Object { Add-WordParagraph $doc '' 12 $false 1 0 22 | Out-Null }
    Add-WordParagraph $doc $title 20 $true 1 0 30 | Out-Null
    1..3 | ForEach-Object { Add-WordParagraph $doc '' 12 $false 1 0 22 | Out-Null }
    @(
        "题    目：$title",
        '院    别：信息工程学院',
        "年级专业：$studentClass",
        "姓    名：$student",
        "学    号：$studentId",
        "指导教师：$teacher        职称：$teacherTitle",
        '起讫日期：2026年7月6日—2026年8月30日'
    ) | ForEach-Object { Add-WordParagraph $doc $_ 15 $false 1 0 30 | Out-Null }

    Add-PageBreak $doc
    Add-WordParagraph $doc $title 18 $true 1 0 26 | Out-Null
    Add-WordParagraph $doc $student 12 $false 1 0 22 | Out-Null
    Add-WordParagraph $doc "三明学院 信息工程学院 $studentClass" 12 $false 1 0 22 | Out-Null
    Add-WordParagraph $doc '摘要' 16 $true 1 0 24 | Out-Null
    $abstract = '针对游戏录屏剪辑流程长、专业工具学习成本高以及云端生成服务存在素材上传和费用依赖等问题，本文设计并实现了一套本地运行的多模态游戏视频智能解说与自动剪辑系统GameNarrator。系统采用前后端分离架构，后端基于Java 21与Spring Boot 3.2构建，前端采用Vue 3与Vite，使用H2或PostgreSQL保存任务、版本和素材元数据。系统将处理过程划分为素材入库、镜头检测、语音转写、画面理解、高光筛选、文案生成、语音合成、时间线规划和视频渲染九个阶段。FFmpeg负责媒体分析与合成，whisper.cpp完成本地语音识别，Ollama中的Qwen2.5-VL完成代表帧理解和原创解说生成，Piper完成中文语音合成。为提高长任务可靠性，系统记录阶段状态、子进度、产物指纹和错误追踪号，支持断点恢复、失败重试和安全取消；为保护用户数据，原始视频、模型和中间产物均可保存在非系统盘，本地模式不要求上传云端。测试结果表明，当前项目包含337项自动化测试，执行结果为0失败、4跳过；前端构建及快速检查脚本可在开发阶段及时发现语法、资源引用和配置问题。该系统完成了从视频输入到可播放MP4输出的闭环，并通过可编辑文案、分镜和时间线保留人工控制能力，能够满足课程设计展示和初级内容创作者的基本使用需求。'
    Add-WordParagraph $doc $abstract 12 $false 3 24 22 | Out-Null
    Add-WordParagraph $doc '关键词：多模态理解；视频自动剪辑；本地大模型；语音识别；FFmpeg' 12 $false 0 0 22 | Out-Null
    Add-WordParagraph $doc 'Abstract' 16 $true 1 0 24 | Out-Null
    $enAbstract = 'This paper designs and implements GameNarrator, a locally deployed multimodal system for intelligent commentary and automatic editing of game videos. The system uses Java 21 and Spring Boot 3.2 on the server side and Vue 3 with Vite on the client side. Its workflow contains nine traceable stages: media ingestion, scene detection, speech transcription, visual understanding, highlight selection, script generation, text-to-speech, timeline planning, and video rendering. FFmpeg handles media processing, whisper.cpp performs local speech recognition, Qwen2.5-VL served by Ollama analyzes representative frames and generates original narration, and Piper synthesizes Chinese speech. Stage checkpoints, artifact fingerprints, resumable execution, bounded external processes, and safe cancellation improve reliability. User media and model files can remain on a non-system disk without mandatory cloud upload. The implemented system forms a complete path from source video to playable MP4 output while retaining manual control over scripts, storyboards, and timelines.'
    Add-WordParagraph $doc $enAbstract 11 $false 3 24 20 | Out-Null
    Add-WordParagraph $doc 'Key words: multimodal understanding; automatic video editing; local large model; speech recognition; FFmpeg' 11 $false 0 0 20 | Out-Null

    Add-PageBreak $doc
    Add-WordParagraph $doc '目    录' 18 $true 1 0 26 | Out-Null
    $tocRange = $doc.Range($doc.Content.End - 1, $doc.Content.End - 1)
    $null = $doc.TablesOfContents.Add($tocRange, $true, 1, 3)
    Add-PageBreak $doc

    Add-WordHeading $doc '第一章 绪论' 1 | Out-Null
    Add-WordHeading $doc '1.1 研究背景与意义' 2 | Out-Null
    Add-WordParagraph $doc '短视频与游戏直播的发展使视频内容生产从专业团队逐渐扩展到普通玩家。传统剪辑通常需要人工浏览长时间录屏、定位高光、整理字幕、撰写解说、录制配音并反复调整时间线。该过程机械操作多，对剪辑经验和设备软件都有要求。现有在线人工智能工具能够降低部分门槛，但上传大体积游戏视频会带来网络等待、服务费用和隐私风险，而且模型输出与素材时间轴之间常缺少可解释的对应关系。' | Out-Null
    Add-WordParagraph $doc '本课题将语音、画面、音频能量和游戏事件作为互补信息，在本地计算机上完成分析与合成。系统既自动生成结果，也保存每个阶段的证据和中间产物，使用户能够查看“为什么选择这一段”、修改文案和重新渲染。该设计对学习多模态处理、任务编排、数据库设计和工程可靠性具有综合实践价值。' | Out-Null
    Add-WordHeading $doc '1.2 国内外相关技术现状' 2 | Out-Null
    Add-WordParagraph $doc '视频自动剪辑已从基于固定规则的镜头拼接发展到结合深度学习的场景识别、语音转写和生成式文案。FFmpeg等开源媒体框架提供稳定的解码、滤镜和编码能力；Whisper系列模型提升了复杂声音环境下的语音转写质量；视觉语言模型能够同时理解图像与文本；神经网络语音合成则降低了解说音频制作成本。当前技术仍存在三类工程问题：不同模型输出格式不统一，长视频推理耗时且容易中断，自动生成结果需要人工复核。' | Out-Null
    Add-WordParagraph $doc 'GameNarrator没有把模型调用作为孤立功能，而是通过阶段状态机、统一时间线和产物注册机制把各工具组织成可恢复的工作流。系统默认采用本地开源组件，并允许通过配置替换程序路径和模型名称，适合在消费级Windows计算机上部署。' | Out-Null
    Add-WordHeading $doc '1.3 研究内容' 2 | Out-Null
    @('分析视频创作者从录屏到成片的核心需求，划定自动化与人工编辑的功能边界。','设计九阶段多模态处理流程、领域数据模型、文件存储结构和异常恢复机制。','实现视频分析、语音转写、视觉理解、高光评分、文案生成、配音、时间线与渲染模块。','通过自动化测试、构建检查和典型流程验证系统的正确性与可用性。') | ForEach-Object { Add-WordBullet $doc $_ | Out-Null }

    Add-PageBreak $doc
    Add-WordHeading $doc '第二章 需求分析与关键技术' 1 | Out-Null
    Add-WordHeading $doc '2.1 用户与业务需求' 2 | Out-Null
    Add-WordParagraph $doc '系统主要面向游戏玩家、动漫与二次元内容爱好者以及缺少专业剪辑经验的初级创作者。核心场景是用户选择本地视频，设置目标时长与解说风格，启动自动处理，查看阶段进度，在分镜或文案页面复核结果，最后预览并导出MP4。为降低学习成本，常用流程集中在任务创建、进度、结果预览三个入口；模型管理、素材授权、诊断和高级时间线等功能按需展开。' | Out-Null
    Add-WordHeading $doc '2.2 功能需求' 2 | Out-Null
    @('支持MP4、MOV、MKV和WEBM上传，校验格式、大小与存储空间。','读取分辨率、帧率、编码和时长，完成镜头切分、抽帧与音频特征分析。','生成带时间戳的转写文本、视觉描述、事件类型和高光评分。','按高光生成结构化原创解说，并支持编辑、重写和版本切换。','生成本地中文配音，规划字幕、原声、配音、转场与特效时间线。','使用软件编码或NVENC硬件加速渲染，提供网页预览和文件导出。','显示阶段与子进度，支持取消、重试、重启恢复和诊断日志。') | ForEach-Object { Add-WordBullet $doc $_ | Out-Null }
    Add-WordHeading $doc '2.3 非功能需求' 2 | Out-Null
    Add-WordParagraph $doc '可靠性方面，已完成阶段必须通过文件存在性、JSON可解析性、文件大小和SHA-256指纹验证，损坏时从最早失效阶段重新计算。安全性方面，上传路径必须限制在配置的存储根目录，网络素材仅接受用户具有使用权的来源，Cookie等凭证不得写入日志。可维护性方面，模型地址、命令路径、超时、并发数和存储位置均通过配置管理。易用性方面，错误提示同时提供用户可执行的解决建议与开发者追踪号。' | Out-Null
    Add-WordHeading $doc '2.4 关键技术' 2 | Out-Null
    Add-WordParagraph $doc '后端采用Spring Boot、Spring MVC、Spring Data JPA与Bean Validation，Flyway管理数据库结构演进；前端采用Vue 3组件化构建，Vite提供开发服务器和生产构建。媒体层使用FFmpeg与ffprobe完成探测、抽帧、音频提取、滤镜、字幕、混音和编码。智能层使用whisper.cpp进行语音识别，Ollama统一承载Qwen2.5-VL视觉与文本推理，Piper生成中文WAV。各外部程序均采用超时限制、并发读取输出与进程树终止，避免子进程阻塞主任务。' | Out-Null

    Add-PageBreak $doc
    Add-WordHeading $doc '第三章 系统总体设计' 1 | Out-Null
    Add-WordHeading $doc '3.1 系统架构' 2 | Out-Null
    Add-WordParagraph $doc '系统划分为表示层、应用层、领域服务层、基础设施层和数据层。表示层由Vue页面和REST接口组成；应用层负责任务创建、流程编排和权限上下文；领域服务层包含镜头、转写、视觉、高光、文案、配音、时间线与渲染模块；基础设施层封装FFmpeg、Ollama、whisper.cpp、Piper和文件系统；数据层使用JPA、Flyway以及H2或PostgreSQL。分层使模型与媒体工具可以独立替换，也便于对每个阶段进行单元测试。' | Out-Null
    Add-WordHeading $doc '3.2 九阶段工作流' 2 | Out-Null
    Add-WordParagraph $doc '任务创建后依次执行素材入库、镜头检测、语音转写、画面理解、高光筛选、文案生成、AI配音、时间线规划和视频渲染。每个阶段具有PENDING、RUNNING、COMPLETED、FAILED等状态，并记录当前项、总项、百分比和操作说明。后一阶段只读取已验证的前置产物。应用重启时，恢复器扫描未完成任务，从最近有效检查点继续执行。取消操作先持久化终态，再终止已登记的外部进程，避免退出码覆盖用户决定。' | Out-Null
    Add-WordHeading $doc '3.3 数据库与文件存储设计' 2 | Out-Null
    Add-WordParagraph $doc '数据库保存任务、阶段、素材、脚本版本、分镜放置、时间线、导出记录、用户与审计等结构化元数据。项目目前包含46个Flyway迁移文件，保证不同版本数据库可按顺序升级。大体积视频、帧图、WAV和MP4不直接写入数据库，而是保存在storage根目录下，以任务UUID隔离。表中只保存相对storage_key，服务端解析后再次验证目标路径仍位于安全根目录内，从而降低目录穿越和误删风险。' | Out-Null
    Add-WordHeading $doc '3.4 接口与前端交互设计' 2 | Out-Null
    Add-WordParagraph $doc 'REST接口覆盖任务、素材、模型设置、分镜、脚本、时间线、导出和诊断等资源。前端通过统一API服务访问后端，并对加载、空数据、失败和重试状态给出明确反馈。开发环境由Vite提供热更新，生产构建则将资源复制到Spring Boot静态目录。公共脚本通过HTML标签加载，参与Vue编译的模块位于src目录，以避免将public目录文件错误地作为源码导入。' | Out-Null

    Add-PageBreak $doc
    Add-WordHeading $doc '第四章 关键模块实现与系统测试' 1 | Out-Null
    Add-WordHeading $doc '4.1 多模态素材分析' 2 | Out-Null
    Add-WordParagraph $doc '系统首先调用ffprobe获取媒体参数，再利用场景变化阈值生成候选镜头。除均匀覆盖抽帧外，场景突变、音频峰值和削波位置会生成事件锚点；模型先分析中心帧，只有当中心帧出现明确事件、OCR文本或较高兴奋度时才扩展到前后帧，减少无效推理。语音部分将音频转为16 kHz单声道WAV，输出TXT、SRT和结构化分段。视觉描述、转写关键词、OCR和音频能量最终在统一时间范围内对齐。' | Out-Null
    Add-WordHeading $doc '4.2 高光筛选与文案生成' 2 | Out-Null
    Add-WordParagraph $doc '高光评分融合视觉评分、事件权重、音频能量、识别文本关键词和OCR证据，并通过时间距离去除重复镜头，再在用户目标时长内选择片段。用户可锁定必须保留的镜头或排除无关片段，人工决定在重新筛选后仍然有效。文案模块以片段证据、风格、时长和原创性约束构造结构化请求，生成解说、字幕和特效建议。对本地模型返回的HTTP 404，系统会校验运行时模型是否已经安装，并将历史云端模型名与当前本地模型分开显示，防止把记录模型误当成执行模型。' | Out-Null
    Add-WordHeading $doc '4.3 配音、时间线与渲染' 2 | Out-Null
    Add-WordParagraph $doc 'Piper按段生成WAV并记录音色、语速、情绪和音高参数。时间线将源视频区间、解说音频、字幕、转场、音效和贴图转换为统一的毫秒坐标，检测配音是否超出镜头可用时长。渲染器根据设备能力选择NVENC或软件编码，使用trim、concat、xfade、amix和字幕滤镜完成合成。动态字幕另行输出ASS，同时保留SRT软字幕，使关闭烧录后仍可在播放器中选择字幕轨。' | Out-Null
    Add-WordHeading $doc '4.4 容错、诊断与快速检查' 2 | Out-Null
    Add-WordParagraph $doc '外部模型或工具不可用时，任务不会无限等待。服务记录阶段、HTTP状态、依赖名称和追踪号，并给出启动依赖、安装模型或检查授权的解决建议。诊断页检查数据库迁移、磁盘、FFmpeg、Ollama、Whisper、Piper和前后端构建状态。项目新增快速检查脚本scripts/check-fast.ps1，可在完整测试前执行配置、JavaScript语法、关键资源引用和少量高价值测试，缩短日常排错时间；只有涉及后端行为变更时才运行完整Maven测试。' | Out-Null
    Add-WordHeading $doc '4.5 测试结果' 2 | Out-Null
    Add-WordParagraph $doc '项目测试覆盖任务生命周期、路径归属、工作流产物、模型设置、导演评审、素材放置、异常处理和渲染关键路径。最近一次完整Maven测试共执行337项，其中0项失败、0项错误、4项因缺少对应外部环境而跳过。前端使用Node Test与Vitest验证公共模块、任务恢复、导演评审状态和简化编辑器逻辑，并通过Vite生产构建检查资源依赖。测试说明核心业务在模拟依赖条件下能够稳定运行；真实长视频的速度和成片主观质量仍受显卡、素材长度、模型大小与提示词影响，不能仅由单元测试替代。' | Out-Null

    Add-PageBreak $doc
    Add-WordHeading $doc '第五章 结论与展望' 1 | Out-Null
    Add-WordParagraph $doc '本文完成了GameNarrator多模态游戏视频智能解说与自动剪辑系统的需求分析、架构设计和关键功能实现。系统以可追踪的九阶段流程连接媒体处理、语音识别、视觉语言模型、文本生成、语音合成和视频渲染，在本地设备上形成从视频输入到MP4输出的完整闭环。阶段检查点、产物指纹、取消与恢复机制改善了长任务可靠性；相对路径、授权状态和本地存储策略降低了素材泄露与误操作风险；分镜、脚本和时间线编辑则为自动结果保留了人工修正入口。' | Out-Null
    Add-WordParagraph $doc '后续工作可从三个方向展开：第一，建立包含不同游戏类型、分辨率和音频条件的标准评测集，量化高光命中率、字幕时间误差、渲染实时率和用户修改次数；第二，根据显存和任务类型自动选择模型量化级别与抽帧密度，减少低配置设备等待时间；第三，进一步简化首页流程，通过渐进式界面仅在用户需要时展示高级素材、模型和时间线功能，同时完善模板共享与跨设备项目同步。' | Out-Null

    Add-PageBreak $doc
    Add-WordHeading $doc '参考文献' 1 | Out-Null
    @(
        '[1] OLLAMA. Ollama Documentation[EB/OL]. https://docs.ollama.com/, 2026-09-07.',
        '[2] RADFORD A, KIM J W, XU T, et al. Robust Speech Recognition via Large-Scale Weak Supervision[C]//Proceedings of the 40th International Conference on Machine Learning. Honolulu: PMLR, 2023: 28492-28518.',
        '[3] FFMPEG DEVELOPERS. FFmpeg Documentation[EB/OL]. https://ffmpeg.org/documentation.html, 2026-09-07.',
        '[4] WALLS C. Spring Boot Reference Documentation[EB/OL]. https://docs.spring.io/spring-boot/reference/, 2026-09-07.',
        '[5] YOU E. Vue.js Documentation[EB/OL]. https://vuejs.org/guide/, 2026-09-07.',
        '[6] REDGATE SOFTWARE. Flyway Documentation[EB/OL]. https://documentation.red-gate.com/flyway, 2026-09-07.',
        '[7] RHASSpy COMMUNITY. Piper: A Fast, Local Neural Text to Speech System[EB/OL]. https://github.com/rhasspy/piper, 2026-09-07.',
        '[8] GB/T 7714—2015, 信息与文献 参考文献著录规则[S]. 北京：中国标准出版社，2015.',
        '[9] 三明学院信息工程学院. 2023级学年设计模板[Z]. 三明：三明学院，2026.'
    ) | ForEach-Object { Add-WordParagraph $doc $_ 10.5 $false 0 0 18 | Out-Null }

    Add-PageBreak $doc
    Add-WordHeading $doc '指导教师评分' 1 | Out-Null
    Add-WordParagraph $doc '该部分由指导教师填写。' 12 $false 0 0 22 | Out-Null
    $table = $doc.Tables.Add($doc.Range($doc.Content.End - 1, $doc.Content.End - 1), 7, 3)
    $table.Borders.Enable = 1
    $values = @(
        @('项目','评价内容','分值'),
        @('论文部分（70%）','选题研究背景',''),
        @('论文部分（70%）','需求分析',''),
        @('论文部分（70%）','系统设计与实现',''),
        @('论文部分（70%）','创新性',''),
        @('作品/系统（30%）','功能、界面与设计',''),
        @('总分','','')
    )
    for($i=1; $i -le 7; $i++){ for($j=1; $j -le 3; $j++){ $table.Cell($i,$j).Range.Text=$values[$i-1][$j-1]; $table.Cell($i,$j).Range.Font.NameFarEast='宋体'; $table.Cell($i,$j).Range.Font.Size=11 } }
    Add-WordParagraph $doc '指导教师评语：' 12 $true 0 0 22 | Out-Null
    1..6 | ForEach-Object { Add-WordParagraph $doc ' ' 12 $false 0 0 22 | Out-Null }

    foreach($s in $doc.Sections){
        $footer=$s.Footers.Item(1).Range
        $footer.ParagraphFormat.Alignment=1
        $footer.Fields.Add($footer,-1,'PAGE',$true) | Out-Null
    }
    $doc.TablesOfContents.Item(1).Update()
    $doc.Fields.Update() | Out-Null
    $docPath = Join-Path $output 'GameNarrator学年设计论文.docx'
    $pdfPath = Join-Path $output 'GameNarrator学年设计论文.pdf'
    $doc.SaveAs2($docPath,16)
    $doc.ExportAsFixedFormat($pdfPath,17)
    $pageCount = $doc.ComputeStatistics(2)
    $wordCount = $doc.ComputeStatistics(0)
    $doc.Close($false)
}
finally {
    $word.Quit()
}

function Get-OleRgb([int]$Red,[int]$Green,[int]$Blue) { return $Red + 256*$Green + 65536*$Blue }
$ink = Get-OleRgb 17 17 17
$cream = Get-OleRgb 255 249 235
$pink = Get-OleRgb 255 73 161
$cyan = Get-OleRgb 55 190 235
$yellow = Get-OleRgb 255 230 75
$muted = Get-OleRgb 79 89 105

function Add-PptTextBox {
    param($Slide,[double]$Left,[double]$Top,[double]$Width,[double]$Height,[string]$Text,[double]$Size=22,[bool]$Bold=$false,[int]$Color=$ink,[int]$Align=1)
    $shape=$Slide.Shapes.AddTextbox(1,$Left,$Top,$Width,$Height)
    $shape.TextFrame.TextRange.Text=$Text
    $shape.TextFrame.TextRange.Font.Name='Microsoft YaHei'
    $shape.TextFrame.TextRange.Font.Size=$Size
    $shape.TextFrame.TextRange.Font.Bold=[int]$Bold
    $shape.TextFrame.TextRange.Font.Color.RGB=$Color
    $shape.TextFrame.TextRange.ParagraphFormat.Alignment=$Align
    $shape.TextFrame.WordWrap=-1
    $shape.TextFrame.MarginLeft=6; $shape.TextFrame.MarginRight=6
    return $shape
}

function Add-PptBaseSlide {
    param($Presentation,[string]$TitleText,[string[]]$Bullets,[string]$Tag='GAMENARRATOR')
    $slide=$Presentation.Slides.Add($Presentation.Slides.Count+1,12)
    $bg=$slide.Background.Fill; $bg.ForeColor.RGB=$cream; $bg.Solid()
    $tagBox=$slide.Shapes.AddShape(1,54,24,170,28); $tagBox.Fill.ForeColor.RGB=$cyan; $tagBox.Fill.Solid(); $tagBox.Line.ForeColor.RGB=$ink; $tagBox.Line.Weight=2.5
    Add-PptTextBox $slide 62 28 154 18 'GAME · NARRATOR' 10 $true $ink 2 | Out-Null
    Add-PptTextBox $slide 54 63 840 58 $TitleText 28 $true $ink 1 | Out-Null
    $shadow=$slide.Shapes.AddShape(1,76,142,804,326); $shadow.Fill.ForeColor.RGB=$pink; $shadow.Fill.Solid(); $shadow.Line.Visible=0
    $card=$slide.Shapes.AddShape(1,64,130,804,326); $card.Fill.ForeColor.RGB=0xFFFFFF; $card.Fill.Solid(); $card.Line.ForeColor.RGB=$ink; $card.Line.Weight=3
    $body=Add-PptTextBox $slide 92 157 746 270 (($Bullets | ForEach-Object { "■ $_" }) -join "`r`n") 18 $false $ink 1
    $body.TextFrame.TextRange.ParagraphFormat.SpaceAfter=12
    Add-PptTextBox $slide 740 505 150 20 $Tag 9 $true $muted 3 | Out-Null
    Add-PptTextBox $slide 55 505 80 20 ([string]$slide.SlideIndex).PadLeft(2,'0') 9 $true $muted 1 | Out-Null
    return $slide
}

function Add-PptScreenshotSlide {
    param($Presentation,[string]$TitleText,[string[]]$Bullets,[string]$ImagePath,[int]$Accent=$cyan)
    $slide=$Presentation.Slides.Add($Presentation.Slides.Count+1,12)
    $slide.Background.Fill.ForeColor.RGB=$cream; $slide.Background.Fill.Solid()
    $tagBox=$slide.Shapes.AddShape(1,54,24,170,28); $tagBox.Fill.ForeColor.RGB=$Accent; $tagBox.Fill.Solid(); $tagBox.Line.ForeColor.RGB=$ink; $tagBox.Line.Weight=2.5
    Add-PptTextBox $slide 62 28 154 18 'REAL UI · 实际界面' 10 $true $ink 2 | Out-Null
    Add-PptTextBox $slide 54 63 840 58 $TitleText 28 $true $ink 1 | Out-Null
    $shadow=$slide.Shapes.AddShape(1,62,152,510,304); $shadow.Fill.ForeColor.RGB=$pink; $shadow.Fill.Solid(); $shadow.Line.Visible=0
    $card=$slide.Shapes.AddShape(1,50,140,510,304); $card.Fill.ForeColor.RGB=0xFFFFFF; $card.Fill.Solid(); $card.Line.ForeColor.RGB=$ink; $card.Line.Weight=3
    $body=Add-PptTextBox $slide 74 166 462 250 (($Bullets | ForEach-Object { "■ $_" }) -join "`r`n") 16 $false $ink 1
    $body.TextFrame.TextRange.ParagraphFormat.SpaceAfter=10
    $photoShadow=$slide.Shapes.AddShape(1,657,133,205,335); $photoShadow.Fill.ForeColor.RGB=$Accent; $photoShadow.Fill.Solid(); $photoShadow.Line.Visible=0
    $photo=$slide.Shapes.AddPicture($ImagePath,$false,$true,642,118,205,335); $photo.Line.ForeColor.RGB=$ink; $photo.Line.Weight=3
    Add-PptTextBox $slide 740 505 150 20 'GAMENARRATOR' 9 $true $muted 3 | Out-Null
    Add-PptTextBox $slide 55 505 80 20 ([string]$slide.SlideIndex).PadLeft(2,'0') 9 $true $muted 1 | Out-Null
    return $slide
}

$ppt = New-Object -ComObject PowerPoint.Application
try {
    $pres=$ppt.Presentations.Add()
    $pres.PageSetup.SlideWidth=960; $pres.PageSetup.SlideHeight=540
    $cover=$pres.Slides.Add(1,12)
    $cover.Background.Fill.ForeColor.RGB=$cream; $cover.Background.Fill.Solid()
    $coverShadow=$cover.Shapes.AddShape(1,82,92,808,304); $coverShadow.Fill.ForeColor.RGB=$pink; $coverShadow.Fill.Solid(); $coverShadow.Line.Visible=0
    $coverCard=$cover.Shapes.AddShape(1,66,76,808,304); $coverCard.Fill.ForeColor.RGB=0xFFFFFF; $coverCard.Fill.Solid(); $coverCard.Line.ForeColor.RGB=$ink; $coverCard.Line.Weight=4
    $accent=$cover.Shapes.AddShape(1,66,76,808,44); $accent.Fill.ForeColor.RGB=$yellow; $accent.Fill.Solid(); $accent.Line.ForeColor.RGB=$ink; $accent.Line.Weight=4
    Add-PptTextBox $cover 95 90 750 22 'GAME · NARRATOR  /  2023级学年设计答辩' 12 $true $ink 2 | Out-Null
    Add-PptTextBox $cover 105 150 730 112 $title 30 $true $ink 2 | Out-Null
    Add-PptTextBox $cover 150 290 640 60 "汇报人：$student    学号：$studentId`r`n$studentClass    指导教师：$teacher" 15 $true $muted 2 | Out-Null
    Add-PptTextBox $cover 740 505 150 20 'GAMENARRATOR' 9 $true $ink 3 | Out-Null

    Add-PptBaseSlide $pres '01 选题背景' @('游戏录屏通常很长，人工找高光、写文案、配音和对齐时间线耗时。','在线AI服务依赖上传、网络与付费额度，私有素材存在外传顾虑。','本课题目标是在本地电脑完成从视频输入到MP4输出的完整闭环。') | Out-Null
    Add-PptScreenshotSlide $pres '02 用户需求与设计目标' @('面向游戏玩家和初级内容创作者。','核心路径：导入 → 自动处理 → 复核 → 导出。','高级能力按需展开，降低首次使用成本。','右侧为项目真实首页。') (Join-Path $PSScriptRoot '..\docs\screenshots\home.png') $cyan | Out-Null
    Add-PptBaseSlide $pres '03 系统总体架构' @('前端：Vue 3 + Vite，负责任务创建、进度、分镜、预览与设置。','后端：Java 21 + Spring Boot 3.2，提供REST接口和任务编排。','智能与媒体：Ollama、Qwen2.5-VL、whisper.cpp、Piper、FFmpeg。','数据：JPA + Flyway，支持H2与PostgreSQL；大文件保存在安全存储根目录。') | Out-Null

    $flow=Add-PptBaseSlide $pres '04 九阶段自动处理流程' @() 
    $labels=@('素材入库','镜头检测','语音转写','画面理解','高光筛选','文案生成','AI配音','时间线','视频渲染')
    for($i=0;$i -lt $labels.Count;$i++){
        $row=[math]::Floor($i/5); $col=$i%5; $x=55+$col*175; $y=150+$row*145
        $box=$flow.Shapes.AddShape(5,$x,$y,135,62); $box.Fill.ForeColor.RGB=if($i%2 -eq 0){0xD96C39}else{0x3F6B75}; $box.Fill.Solid(); $box.Line.Visible=0
        Add-PptTextBox $flow $x ($y+15) 135 35 ("$($i+1). $($labels[$i])") 15 $true 0xFFFFFF 2 | Out-Null
        if($i -lt 4 -or ($i -ge 5 -and $i -lt 8)){ Add-PptTextBox $flow ($x+135) ($y+17) 40 30 '→' 19 $true 0x8A92A2 2 | Out-Null }
    }
    Add-PptTextBox $flow 650 365 230 55 '每阶段保存状态、子进度、日志与产物指纹' 14 $false 0x25324A 2 | Out-Null

    Add-PptScreenshotSlide $pres '05 多模态素材分析' @('支持本地视频与授权平台直链导入。','FFmpeg完成探测、镜头检测和抽帧。','Whisper输出带时间戳的转写。','视觉模型生成描述、OCR与事件评分。') (Join-Path $PSScriptRoot '..\docs\screenshots\平台导入.png') $yellow | Out-Null
    Add-PptBaseSlide $pres '06 高光与原创解说' @('融合视觉、事件、音频能量、字幕关键词和OCR证据进行评分。','去除时间过近的重复镜头，并按目标时长选择候选片段。','结构化提示词生成解说、字幕与特效建议，限制照抄和模仿。','用户可以锁定、排除、编辑或重写，重新运行后仍保留人工决定。') | Out-Null
    Add-PptBaseSlide $pres '07 配音、时间线与渲染' @('Piper按段生成本地中文WAV，支持音色、语速、情绪和音高。','统一毫秒时间线对齐原视频、解说、字幕、转场、音效与贴图。','FFmpeg使用trim、xfade、amix和字幕滤镜合成，支持NVENC加速。','输出可播放MP4、SRT软字幕和ASS动态字幕。') | Out-Null
    Add-PptBaseSlide $pres '08 长任务可靠性设计' @('阶段状态机：PENDING / RUNNING / COMPLETED / FAILED / CANCELLED。','检查文件大小、JSON结构和SHA-256指纹，损坏时从最早失效阶段重算。','应用重启后从最近有效检查点继续，不重复执行已验证阶段。','取消先保存终态，再终止外部进程树；外部调用设置超时并持续读取输出。') | Out-Null
    Add-PptScreenshotSlide $pres '09 本地模型与数据安全' @('只显示已安装且可运行的本地引擎。','视频、模型和中间产物可放在非系统盘。','历史记录模型与运行模型分开显示。','相对路径与授权状态共同保护素材。') (Join-Path $PSScriptRoot '..\docs\screenshots\AI_设置.png') $yellow | Out-Null
    Add-PptBaseSlide $pres '10 数据库与版本设计' @('当前包含46个Flyway迁移，结构变更可追踪、可重复部署。','核心数据包括任务、阶段、产物、素材、文案版本、分镜、时间线和导出。','任务UUID隔离工作目录；大文件放文件系统，数据库保存元数据。','版本树支持文案与项目修订，切换上游版本会使相关下游产物失效。') | Out-Null
    Add-PptScreenshotSlide $pres '11 界面与交互原则' @('首页突出任务、进度和成片预览。','阶段状态持久化，重启后可继续。','失败信息包含解决建议和追踪号。','分镜与时间线保留人工复核入口。') (Join-Path $PSScriptRoot '..\docs\screenshots\剪辑任务.png') $pink | Out-Null
    Add-PptBaseSlide $pres '12 测试与验证结果' @('后端完整测试：337项，0失败，0错误，4项因外部环境跳过。','覆盖任务恢复、路径归属、模型设置、导演评审、异常处理和渲染关键路径。','前端使用Node Test、Vitest与Vite生产构建检查模块和资源引用。','快速检查脚本先发现常见问题，再按变更范围运行完整测试。') | Out-Null
    Add-PptBaseSlide $pres '13 项目特点与不足' @('特点：本地多模态闭环、可解释高光、断点恢复、人工可编辑。','特点：模型与工具路径配置化，适配消费级Windows电脑。','不足：真实视频处理速度受显卡、素材时长和模型大小影响。','不足：高光与文案质量仍需更大规模评测集和用户评价验证。') | Out-Null
    Add-PptBaseSlide $pres '14 总结与展望' @('完成从视频导入、理解、解说到渲染导出的九阶段系统。','用工程化检查点和诊断机制提高本地AI长任务的可用性。','后续建立标准评测集，自动匹配模型与抽帧密度，并继续简化交互。','答辩演示建议：创建任务 → 查看阶段进度 → 修改文案 → 预览成片。') | Out-Null

    $end=$pres.Slides.Add($pres.Slides.Count+1,12); $end.Background.Fill.ForeColor.RGB=$cream; $end.Background.Fill.Solid()
    $endShadow=$end.Shapes.AddShape(1,186,166,600,190); $endShadow.Fill.ForeColor.RGB=$cyan; $endShadow.Fill.Solid(); $endShadow.Line.Visible=0
    $endCard=$end.Shapes.AddShape(1,170,150,600,190); $endCard.Fill.ForeColor.RGB=$yellow; $endCard.Fill.Solid(); $endCard.Line.ForeColor.RGB=$ink; $endCard.Line.Weight=4
    Add-PptTextBox $end 200 190 540 70 '谢谢观看' 40 $true $ink 2 | Out-Null
    Add-PptTextBox $end 200 275 540 35 '请各位老师批评指正' 18 $true $ink 2 | Out-Null
    $pptPath=Join-Path $output 'GameNarrator学年设计答辩.pptx'
    $pres.SaveAs($pptPath,24)
    $slideCount=$pres.Slides.Count
    $pres.Close()
}
finally {
    $ppt.Quit()
}

$readme=@"
# GameNarrator 学年设计交付说明

- `GameNarrator学年设计论文.docx`：按信息工程学院模板结构生成的可编辑论文。
- `GameNarrator学年设计论文.pdf`：用于检查版式和提交预览。
- `GameNarrator学年设计答辩.pptx`：16:9 答辩演示文稿，共 $slideCount 页。

姓名、学号、班级和指导教师已依据用户提供的论文文件填写；提交前只需补充指导教师职称。PPT已嵌入项目仓库中的真实设备界面截图，并采用与当前前端一致的 Memphis 配色和粗边框卡片风格。论文正文中的测试数量来自本项目最近一次完整测试结果，不包含虚构的准确率或性能数据。
"@
[IO.File]::WriteAllText((Join-Path $output '交付说明.md'),$readme,[Text.UTF8Encoding]::new($false))
"DOCX=$docPath`nPDF=$pdfPath`nPPTX=$pptPath`nPAGES=$pageCount`nWORDS=$wordCount`nSLIDES=$slideCount"
