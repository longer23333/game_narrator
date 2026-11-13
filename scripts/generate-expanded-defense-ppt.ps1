param([string]$OutputDirectory = (Join-Path $PSScriptRoot '..\docs\deliverables'))
$ErrorActionPreference='Stop'
$out=[IO.Path]::GetFullPath($OutputDirectory); New-Item -ItemType Directory -Force -Path $out | Out-Null
$title='多模态游戏视频智能解说与自动剪辑系统的设计与实现'
$student='陈华熠'; $studentId='20230862133'; $studentClass='2023级网络工程班'; $teacher='张标汉'
function RGB([int]$r,[int]$g,[int]$b){$r+256*$g+65536*$b}
$ink=RGB 17 17 17; $cream=RGB 255 249 235; $pink=RGB 255 73 161; $cyan=RGB 55 190 235; $yellow=RGB 255 230 75; $muted=RGB 79 89 105; $white=RGB 255 255 255
function Text($s,$x,$y,$w,$h,$t,$size=20,$bold=$false,$color=$ink,$align=1){$o=$s.Shapes.AddTextbox(1,$x,$y,$w,$h);$o.TextFrame.TextRange.Text=$t;$o.TextFrame.TextRange.Font.Name='Microsoft YaHei';$o.TextFrame.TextRange.Font.Size=$size;$o.TextFrame.TextRange.Font.Bold=[int]$bold;$o.TextFrame.TextRange.Font.Color.RGB=$color;$o.TextFrame.TextRange.ParagraphFormat.Alignment=$align;$o.TextFrame.WordWrap=-1;$o.TextFrame.MarginLeft=5;$o.TextFrame.MarginRight=5;$o}
function Rect($s,$x,$y,$w,$h,$fill,$line=$ink,$weight=3){$o=$s.Shapes.AddShape(1,$x,$y,$w,$h);$o.Fill.ForeColor.RGB=$fill;$o.Fill.Solid();$o.Line.ForeColor.RGB=$line;$o.Line.Weight=[single]$weight;$o}
function Base($p,$titleText,$tag='GAMENARRATOR',$accent=$cyan){$s=$p.Slides.Add($p.Slides.Count+1,12);$s.Background.Fill.ForeColor.RGB=$cream;$s.Background.Fill.Solid();Rect $s 54 24 180 28 $accent $ink 2.5|Out-Null;Text $s 60 28 168 18 $tag 10 $true $ink 2|Out-Null;Text $s 54 63 840 55 $titleText 28 $true|Out-Null;Text $s 54 505 50 18 ([string]$s.SlideIndex).PadLeft(2,'0') 9 $true $muted|Out-Null;Text $s 740 505 150 18 'GAMENARRATOR' 9 $true $muted 3|Out-Null;$s}
function Bullets($p,$titleText,[string[]]$items,$accent=$cyan){$s=Base $p $titleText 'GAME · NARRATOR' $accent;Rect $s 78 145 800 310 $pink $pink 0|Out-Null;Rect $s 64 131 800 310 $white $ink 3|Out-Null;$body=Text $s 92 158 744 250 (($items|ForEach-Object{"■ $_"})-join "`r`n") 17 $false;$body.TextFrame.TextRange.ParagraphFormat.SpaceAfter=11;$s}
function Divider($p,$number,$name,$subtitle,$accent){$s=$p.Slides.Add($p.Slides.Count+1,12);$s.Background.Fill.ForeColor.RGB=$cream;$s.Background.Fill.Solid();Rect $s 106 112 760 300 $pink $pink 0|Out-Null;Rect $s 88 94 760 300 $accent $ink 4|Out-Null;Text $s 126 135 150 70 $number 46 $true|Out-Null;Text $s 280 142 510 62 $name 34 $true|Out-Null;Text $s 130 245 660 80 $subtitle 18 $true $ink 1|Out-Null;Text $s 735 360 90 20 'SECTION' 10 $true $ink 3|Out-Null;$s}
function Device($p,$titleText,[string[]]$items,$image,$accent=$yellow){$s=Base $p $titleText 'ANDROID · 真实设备截图' $accent;Rect $s 62 152 510 304 $pink $pink 0|Out-Null;Rect $s 50 140 510 304 $white $ink 3|Out-Null;$b=Text $s 75 165 455 250 (($items|ForEach-Object{"■ $_"})-join "`r`n") 16 $false;$b.TextFrame.TextRange.ParagraphFormat.SpaceAfter=10;Rect $s 657 133 205 335 $accent $accent 0|Out-Null;$pic=$s.Shapes.AddPicture($image,$false,$true,642,118,205,335);$pic.Line.ForeColor.RGB=$ink;$pic.Line.Weight=3;$s}
function WebMock($p,$titleText,$mode,[string[]]$notes,$accent=$cyan){$s=Base $p $titleText 'WEB · 当前前端结构图' $accent;Rect $s 49 132 580 320 $pink $pink 0|Out-Null;Rect $s 36 119 580 320 $white $ink 3|Out-Null;Rect $s 36 119 580 45 $yellow $ink 3|Out-Null;Text $s 54 132 190 18 'GAME · NARRATOR  V2.2.58' 11 $true|Out-Null;Text $s 300 132 285 18 '制作视频   平台导入   设置   更多工具' 10 $true $muted 3|Out-Null
  if($mode -eq 'studio'){Text $s 60 184 520 35 '选一段录像，先看成片' 23 $true|Out-Null;Rect $s 60 235 250 150 $cyan $ink 3|Out-Null;Text $s 78 252 210 25 '制作新视频' 17 $true|Out-Null;Text $s 78 292 210 65 "选择视频`r`n内容类别 · 解说风格`r`n生成视频预览 →" 12 $true|Out-Null;Rect $s 330 235 250 68 $yellow $ink 3|Out-Null;Text $s 348 255 210 22 '运行中任务 · LIVE' 14 $true|Out-Null;Rect $s 330 318 250 67 $white $ink 3|Out-Null;Text $s 348 338 210 22 '最近完成 · HISTORY' 14 $true|Out-Null}
  elseif($mode -eq 'tools'){Text $s 60 184 520 35 '专业工具按需展开' 23 $true|Out-Null;foreach($i in 0..3){$x=60+($i%2)*260;$y=238+[math]::Floor($i/2)*78;$c=@($cyan,$yellow,$pink,$white)[$i];Rect $s $x $y 235 58 $c $ink 3|Out-Null;Text $s ($x+12) ($y+18) 210 22 @('镜头语义搜索','授权素材库','AI 与系统设置','诊断与发布检查')[$i] 13 $true|Out-Null}}
  else{Text $s 60 184 520 35 '项目详情与人工复核' 23 $true|Out-Null;Rect $s 60 235 520 46 $yellow $ink 3|Out-Null;Text $s 75 249 490 20 '导入 → 修剪 → 分镜 → 配音/素材 → 版本 → 渲染' 12 $true|Out-Null;Rect $s 60 302 160 83 $pink $ink 3|Out-Null;Rect $s 238 302 160 83 $cyan $ink 3|Out-Null;Rect $s 416 302 164 83 $yellow $ink 3|Out-Null;Text $s 72 330 135 22 '分镜复核' 14 $true|Out-Null;Text $s 250 330 135 22 '文案与配音' 14 $true|Out-Null;Text $s 428 330 140 22 '时间线渲染' 14 $true|Out-Null}
  $n=Text $s 660 150 240 270 (($notes|ForEach-Object{"■ $_"})-join "`r`n") 14 $false;$n.TextFrame.TextRange.ParagraphFormat.SpaceAfter=9;$s}

$ppt=New-Object -ComObject PowerPoint.Application
try{$p=$ppt.Presentations.Add();$p.PageSetup.SlideWidth=960;$p.PageSetup.SlideHeight=540
  $s=$p.Slides.Add(1,12);$s.Background.Fill.ForeColor.RGB=$cream;$s.Background.Fill.Solid();Rect $s 82 92 808 304 $pink $pink 0|Out-Null;Rect $s 66 76 808 304 $white $ink 4|Out-Null;Rect $s 66 76 808 44 $yellow $ink 4|Out-Null;Text $s 95 90 750 22 'GAME · NARRATOR / 2023级学年设计答辩' 12 $true $ink 2|Out-Null;Text $s 105 150 730 112 $title 30 $true $ink 2|Out-Null;Text $s 145 290 650 60 "汇报人：$student  学号：$studentId`r`n$studentClass  指导教师：$teacher" 15 $true $muted 2|Out-Null
  Bullets $p '汇报目录' @('01 课题背景与系统目标','02 系统架构与核心工作流','03 Web、Windows EXE、Android 三端设计','04 关键实现、可靠性与数据安全','05 测试结果、项目特点与展望') $yellow|Out-Null
  Bullets $p '01 选题背景与问题定义' @('长游戏录屏需要人工找高光、写文案、录配音并对齐时间线。','专业剪辑软件学习成本高，在线AI又依赖上传、网络和额度。','课题目标是在本地形成“视频输入—多模态理解—自动解说—MP4输出”闭环。','系统必须允许人工复核，自动结论不能直接覆盖用户时间线。')|Out-Null
  Bullets $p '02 需求分层与用户路径' @('初次用户：选择视频和风格，一键生成预览。','进阶用户：调整高光、文案、配音、字幕、素材和时间线。','维护人员：检查模型、磁盘、迁移、日志和发布状态。','核心体验只暴露高频功能，其余页面通过“更多工具”进入。') $pink|Out-Null
  Bullets $p '03 总体技术架构' @('表示层：Vue 3 + Vite Web工作台、Android客户端、Windows启动器。','应用层：Spring Boot REST接口、任务编排、身份与权限。','智能层：Ollama/Qwen2.5-VL、whisper.cpp、Piper。','媒体与数据层：FFmpeg、JPA、Flyway、H2/PostgreSQL及文件存储。')|Out-Null
  Bullets $p '04 九阶段核心工作流' @('素材入库 → 镜头检测 → 语音转写 → 画面理解。','高光筛选 → 文案生成 → AI配音 → 时间线规划 → 视频渲染。','每阶段记录状态、百分比、当前项、总项、日志和产物指纹。','重启时从最近有效检查点继续，失败只重算受影响阶段。') $yellow|Out-Null

  Divider $p 'A' '网页版工作台' '浏览器访问完整创作能力；适合电脑端制作、复核、管理与导出。' $cyan|Out-Null
  WebMock $p '05 Web：首页与任务创建' 'studio' @('真实页面结构来自当前 frontend/index.html。','简化为选视频、选类别、选风格、生成预览。','运行任务与历史结果在同一工作台展示。')|Out-Null
  WebMock $p '06 Web：专业工具页面' 'tools' @('镜头搜索：按自然语言寻找片段。','素材库：管理授权、来源与下载状态。','设置：模型角色、安装状态和用量。','诊断：依赖、存储、迁移和追踪日志。') $yellow|Out-Null
  WebMock $p '07 Web：项目详情与编辑' 'editor' @('分镜、文案、配音、素材与版本分步复核。','统一时间线管理字幕、转场、音效与贴图。','预览确认后再导出，保留人工决定。') $pink|Out-Null
  Bullets $p '08 Web：平台导入与素材授权' @('支持无登录HTTP/HTTPS媒体直链以及yt-dlp平台解析。','登录受限内容只接受用户主动提供、按域名过滤的临时Cookie。','开放许可素材与本人授权素材统一登记来源、署名和授权状态。','无符合授权条件的素材时明确跳过，不把网络失败伪装成成功。') $cyan|Out-Null
  Bullets $p '09 Web：后台管理与诊断' @('管理员页面独立于创作工作台，管理用户、项目、配额、云同步与审计。','诊断页汇总FFmpeg、Ollama、Whisper、Piper、磁盘和数据库迁移。','普通用户只看到可执行的修复建议；详细日志使用追踪号关联。','发布就绪度区分RED/YELLOW/GREEN，云同步未启用不阻止本地模式。') $yellow|Out-Null

  Divider $p 'B' 'Windows EXE 应用' '桌面入口负责启动、依赖检查与浏览器工作台编排；核心处理仍由本地后端完成。' $pink|Out-Null
  Bullets $p '10 EXE：产品定位与职责' @('面向不熟悉命令行的Windows用户，提供统一启动入口。','检查Java、FFmpeg、模型服务、端口和数据目录，再启动后端。','打开本机Web工作台，并集中展示启动失败原因。','安装包不把大型模型写入C盘；模型与任务存储位置保持可配置。') $pink|Out-Null
  Bullets $p '11 EXE：启动链路' @('用户启动 GameNarrator.Launcher.exe。','启动器读取配置并检查8081、5173等端口占用。','依赖可用后启动Spring Boot与前端服务，健康检查通过才打开页面。','退出或取消时终止登记的子进程，避免残留服务继续占用端口。') $yellow|Out-Null
  Bullets $p '12 EXE：发布与目录结构' @('launcher采用.NET项目构建，可生成Windows发布目录和安装包。','程序、配置、模型、data与storage分离，升级时保留用户数据。','日志和诊断包可导出，便于定位不同电脑上的依赖问题。','安装验证关注干净机器启动、路径含中文、非C盘部署和版本一致性。') $cyan|Out-Null

  Divider $p 'C' 'Android 客户端' '移动端用于本地创作、电脑协同与结果查看；界面证据来自真实设备截图。' $yellow|Out-Null
  Device $p '13 Android：首页与功能入口' @('本地推理工作台集中剪辑任务、平台导入、素材库和设置。','大按钮和高对比样式适配触控。','任务历史保存在设备端数据库。') (Join-Path $PSScriptRoot '..\docs\screenshots-device\home.png') $cyan|Out-Null
  Device $p '14 Android：平台导入' @('支持手机端媒体直链。','下载后可直接建立剪辑项目。','明确提示登录、Cookie和内容授权边界。') (Join-Path $PSScriptRoot '..\docs\screenshots-device\平台导入.png') $yellow|Out-Null
  Device $p '15 Android：AI模型与能力检查' @('只把已安装且验证可用的能力标为可用。','展示转写、视觉、文案、TTS和编码器状态。','避免“界面有入口但实际不能运行”。') (Join-Path $PSScriptRoot '..\docs\screenshots-device\AI_设置.png') $pink|Out-Null
  Device $p '16 Android：任务阶段与恢复' @('移动端按阶段推进任务。','状态持久化，重启后继续。','下一步操作明确，减少复杂参数暴露。') (Join-Path $PSScriptRoot '..\docs\screenshots-device\剪辑任务.png') $yellow|Out-Null
  Device $p '17 Android：镜头搜索与素材库' @('镜头搜索复用本地语义索引。','素材库统一保存授权和来源。','移动端与电脑端使用一致的业务概念。') (Join-Path $PSScriptRoot '..\docs\screenshots-device\镜头搜索.png') $cyan|Out-Null

  Divider $p 'D' '核心实现与验证' '从多模态理解到可靠渲染，以可解释证据和自动化测试支撑结论。' $pink|Out-Null
  Bullets $p '18 多模态分析与高光筛选' @('均匀覆盖帧结合场景突变、音频峰值和削波锚点。','中心帧命中事件、OCR或高兴奋度后才扩展前后帧，减少无效推理。','视觉、事件、音频能量、字幕关键词与OCR共同参与评分。','锁定与排除属于人工决定，重新筛选后仍然保留。')|Out-Null
  Bullets $p '19 文案、配音、时间线与渲染' @('结构化提示词按片段生成原创解说、字幕和特效建议。','Piper按段生成WAV，支持音色、语速、情绪和音高。','毫秒时间线统一原片、配音、字幕、转场、音效与贴图。','FFmpeg完成trim、xfade、amix、ASS/SRT字幕和NVENC/软件编码。') $yellow|Out-Null
  Bullets $p '20 长任务可靠性与安全' @('产物使用大小、JSON结构和SHA-256指纹验证。','外部进程设置超时、并发读取输出并支持终止进程树。','数据库只保存相对storage_key，删除前验证安全根目录。','历史记录模型与当前执行模型分开显示，降低HTTP 404误判。') $pink|Out-Null
  Bullets $p '21 数据库与版本管理' @('46个Flyway迁移保证数据库结构按版本演进。','任务、阶段、素材、脚本版本、分镜、时间线、导出和审计分表保存。','大视频和中间产物保存在文件系统，数据库只保存元数据。','项目版本树允许回看和切换，上游修改会准确失效相关下游产物。') $cyan|Out-Null
  Bullets $p '22 测试结果与质量边界' @('最近完整后端测试337项：0失败、0错误、4项因外部环境跳过。','前端使用Node Test、Vitest和Vite生产构建检查模块与资源引用。','快速检查脚本优先发现语法、配置和高价值回归问题。','真实长视频速度与主观质量仍受显卡、素材、模型和提示词影响。') $yellow|Out-Null
  Bullets $p '23 项目特点、局限与展望' @('特点：本地多模态闭环、可解释高光、断点恢复、人工可编辑。','特点：Web负责完整创作，EXE负责桌面启动，Android负责移动与协同。','局限：Web桌面实机截图仍需在最终答辩电脑补录，性能评测集规模有限。','展望：自动匹配模型与抽帧密度，继续简化入口并完善跨端同步。') $pink|Out-Null
  Bullets $p '24 总结与演示建议' @('完成从视频输入、理解、解说到MP4输出的九阶段系统。','三种客户端共享后端概念，但各自承担不同使用场景。','现场演示：EXE启动 → Web创建任务 → 查看进度 → 修改文案 → 预览成片。','Android补充展示平台导入、模型检查和任务阶段。') $cyan|Out-Null
  $s=$p.Slides.Add($p.Slides.Count+1,12);$s.Background.Fill.ForeColor.RGB=$cream;$s.Background.Fill.Solid();Rect $s 186 166 600 190 $cyan $cyan 0|Out-Null;Rect $s 170 150 600 190 $yellow $ink 4|Out-Null;Text $s 200 190 540 70 '谢谢观看' 40 $true $ink 2|Out-Null;Text $s 200 275 540 35 '请各位老师批评指正' 18 $true $ink 2|Out-Null
  $pptx=Join-Path $out 'GameNarrator学年设计答辩-三端扩充版.pptx';$pdf=Join-Path $out 'GameNarrator学年设计答辩-三端扩充版.pdf';$p.SaveAs($pptx,24);$p.SaveAs($pdf,32);$count=$p.Slides.Count;$p.Close()
}finally{$ppt.Quit()}
"PPTX=$pptx`nPDF=$pdf`nSLIDES=$count"
