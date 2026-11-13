from pathlib import Path
from docx import Document
from docx.shared import Cm, Pt
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.section import WD_SECTION
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Image, Table, TableStyle, KeepTogether
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY, TA_LEFT
from reportlab.lib import colors
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from PIL import Image as PILImage
import glob, os

ROOT=Path(__file__).resolve().parents[1]; OUT=ROOT/'docs'/'deliverables'; OUT.mkdir(parents=True,exist_ok=True)
DOCX=OUT/'GameNarrator学年设计论文-完整20至30页版.docx'; PDF=OUT/'GameNarrator学年设计论文-完整20至30页版.pdf'
TITLE='多模态游戏视频智能解说与自动剪辑系统的设计与实现'
chapters=[
('第一章 绪论',[
('1.1 研究背景与意义',[
'游戏录屏、直播切片和攻略视频的数量快速增长，内容创作者经常需要从数十分钟甚至数小时的原始视频中寻找击杀、反转、失败、剧情转折等片段。传统流程需要人工反复拖动时间轴，再分别完成字幕、文案、配音、音乐和转场，操作重复且依赖剪辑经验。对于课程创作和个人创作者，软件学习成本、素材管理和长任务等待成为主要负担。',
'云端人工智能工具可以完成部分环节，但大体积视频上传耗时，付费额度和网络状态会直接影响任务，同时私有录屏、语音和账号信息存在外传顾虑。本课题选择本地优先路线，把视频理解、语音识别、文案生成、语音合成与视频渲染连接为一条可恢复工作流，使用户能够在非系统盘保存模型和素材，并保留对分镜、脚本和时间线的人工控制。']),
('1.2 国内外相关技术现状',[
'视频自动剪辑已经从基于固定阈值和模板拼接，发展到结合镜头边界检测、自动语音识别、视觉语言模型和生成式文本的综合流程。FFmpeg提供成熟的解码、滤镜、混音与编码能力；Whisper系列模型改善复杂声音环境下的转写效果；视觉语言模型能够结合画面、OCR和提示词输出语义描述；神经语音合成则降低了解说录制成本。',
'现有方案仍存在模型输出格式不统一、长视频推理容易中断、自动结果缺少证据、工具之间时间坐标难以对齐等工程问题。GameNarrator不把模型调用当作孤立按钮，而是建立阶段状态、产物登记、版本关系和统一时间线，使每一次自动判断都能回到对应镜头和证据。']),
('1.3 研究内容与论文结构',[
'本文围绕需求分析、总体架构、数据库与文件存储、多模态处理、三端交互、可靠性、安全性和测试验证展开。系统实现素材入库、镜头检测、语音转写、画面理解、高光筛选、文案生成、AI配音、时间线规划和视频渲染九个阶段，并提供Web、Windows EXE与Android三类入口。',
'论文第二章说明用户与功能需求，第三章给出系统总体设计，第四章分析关键算法与工作流，第五章介绍三端功能实现，第六章说明数据库、安全与容错设计，第七章给出测试方案与结果，最后总结系统特点、限制和后续改进方向。'])]),
('第二章 需求分析',[
('2.1 用户角色与使用场景',[
'普通创作者希望选择视频、设置目标时长和风格后直接得到可播放结果；进阶创作者需要查看候选镜头、修改文案、调整时间线和替换素材；维护人员关注依赖状态、模型安装、数据库迁移、错误追踪和发布证据。系统采用渐进式交互，高频操作放在主流程，高级能力放在专业工具和设置页。',
'典型场景包括本地录屏自动生成解说视频、从授权平台导入素材、恢复被中断的长任务、在移动端查看电脑处理进度，以及在导出前复核素材许可。所有场景共享项目、任务、阶段、版本和导出记录，避免不同入口形成互不一致的数据。']),
('2.2 功能需求',[
'输入模块支持MP4、MOV、MKV和WEBM等常用容器，校验文件类型、大小、可读性与剩余磁盘空间。分析模块读取分辨率、帧率、编码和时长，完成镜头切分、代表帧抽取、音频能量计算、语音转写、OCR与画面理解。生成模块根据高光证据产生结构化解说、字幕和特效建议。',
'编辑模块允许用户锁定或排除分镜、修改脚本、切换版本、放置素材并调整时间线。输出模块使用软件编码或NVENC硬件加速生成MP4，同时保留SRT软字幕和ASS动态字幕。任务模块显示阶段与子进度，支持暂停式检查点、取消、失败重试和应用重启恢复。']),
('2.3 非功能需求',[
'可靠性要求已经完成的阶段必须经过文件存在性、大小、JSON结构和SHA-256指纹校验，损坏时从最早失效阶段重新计算。性能要求外部程序采用并发读取输出和超时限制，并根据显存与任务类型控制推理并发。可维护性要求模型名称、程序路径、端口、存储目录和超时通过配置管理。',
'安全性要求上传与导出路径必须位于受控根目录，网络素材需要记录来源和许可，远程下载禁止访问本机与局域网地址，访问令牌不得进入普通日志。易用性要求错误信息同时提供用户可执行的处理建议和开发者可查询的追踪号。']),
('2.4 可行性分析',[
'技术方面，项目使用的Spring Boot、Vue、FFmpeg、Ollama、whisper.cpp和Piper均具有明确接口，可以通过进程或HTTP服务组合。硬件方面，系统提供量化模型、抽帧密度和软件编码选项，可在普通Windows电脑运行，显卡只影响速度而不改变基本流程。',
'经济方面，核心依赖以开源工具和本地模型为主，不要求持续购买云端额度。操作方面，用户可以通过Windows启动器和浏览器完成主要流程，Android端用于移动查看与轻量操作。工程风险主要集中在模型体积、显存不足、外部来源不可用和长任务中断，系统分别提供非系统盘安装、能力检测、来源降级与阶段恢复。'])]),
('第三章 系统总体设计',[
('3.1 分层架构设计',[
'系统划分为客户端层、应用服务层、领域处理层、基础设施层和数据层。客户端层由Vue Web、Windows Launcher和Android应用组成；应用服务层提供REST接口、参数校验、任务编排与权限上下文；领域处理层实现九阶段业务；基础设施层封装FFmpeg、Ollama、Whisper、Piper和文件系统；数据层使用JPA、Flyway、H2或PostgreSQL。',
'分层设计把业务状态与外部工具隔离。模型或可执行程序改变时，只需替换对应适配器；工作流通过统一阶段结果读取产物，不依赖某个模型的临时返回格式。这一结构也便于使用模拟实现测试任务生命周期，而不必在每次单元测试中加载大型模型。']),
('3.2 九阶段工作流设计',[
'任务创建后依次执行素材入库、镜头检测、语音转写、画面理解、高光筛选、文案生成、AI配音、时间线规划和视频渲染。每个阶段记录PENDING、RUNNING、COMPLETED、FAILED或CANCELLED状态，并保存当前项、总项、百分比、操作说明和错误追踪号。',
'后一阶段只读取通过验证的前置产物。应用重启时，恢复器扫描未完成任务，按产物指纹确定最近有效检查点；用户取消时先持久化终态，再终止已登记外部进程，避免进程退出码把取消覆盖成失败。']),
('3.3 数据与文件组织',[
'数据库保存任务、阶段、产物、素材、脚本版本、分镜放置、时间线、导出、用户和审计等结构化信息。大体积视频、帧图、WAV和MP4存入storage根目录，以任务UUID隔离；表中只保存相对存储键，服务端解析后再次确认路径仍位于安全根目录。',
'版本对象保存父版本和生成来源。用户修改上游文案后，系统不会直接删除旧配音和旧时间线，而是把相关下游产物标记为失效，重新生成后形成新版本。该设计既支持回滚，也能明确展示某个成片由哪些分镜、脚本和素材构成。']),
('3.4 接口与状态交互',[
'REST接口按任务、素材、模型、分镜、脚本、时间线、导出和诊断划分资源。前端统一处理加载、空数据、失败和重试状态，避免每个页面自行拼接错误信息。任务进度采用持久化状态作为事实来源，页面刷新或三端切换不会改变实际执行结果。',
'开发环境由Vite提供热更新，生产构建资源由Spring Boot托管。public目录中的脚本通过HTML标签引用，参与Vue插件转换的模块放在src目录，从结构上避免“public文件被源码导入”的Vite错误。'])]),
('第四章 关键技术与核心流程实现',[
('4.1 媒体探测与镜头检测',[
'系统先调用ffprobe读取时长、帧率、分辨率、旋转信息、视频编码、音频编码和声道。探测结果被标准化为结构化JSON并登记为阶段产物，后续模块不重复解析命令行文本。输入视频在进入工作目录前完成扩展名、MIME、魔数和可解码性检查。',
'镜头检测同时使用场景变化阈值、均匀采样和音频事件锚点。场景阈值发现明显切换，均匀采样保证长时间平稳画面仍被覆盖，音频峰值补充爆炸、欢呼等事件。相邻候选点合并后选择中心帧作为代表，减少重复视觉推理。']),
('4.2 语音转写与画面理解',[
'音频先转换为16 kHz单声道WAV，再交给whisper.cpp产生带起止时间的分段文本，同时输出TXT、SRT和结构化JSON。系统保留原始识别置信信息，并将过短、重叠或超出媒体时长的分段规范化。',
'视觉模型接收代表帧、邻近帧和任务提示，输出场景描述、人物动作、UI文字、事件类别和兴奋度。系统先分析中心帧，只有出现明确事件、OCR或高兴奋度时才扩展前后帧。返回内容经过JSON解析、字段范围和时间归属校验，失败请求记录模型名、HTTP状态与追踪号。']),
('4.3 高光评分与去重',[
'高光评分融合视觉事件、音频能量、转写关键词、OCR线索和镜头变化强度。不同证据先归一化到统一范围，再按配置权重计算综合分。为了避免连续镜头重复入选，候选片段按照时间距离和内容相似度执行非极大值抑制。',
'最终选择还受到目标视频时长、最短片段、最长片段和内容覆盖约束。用户锁定的镜头优先保留，明确排除的镜头不会因重新评分恢复。每个入选片段保存分项得分和采用理由，使用户能理解自动选择结果。']),
('4.4 文案生成与导演评审',[
'文案模块使用片段证据、上下文、目标风格、可用时长和原创性要求构造结构化请求，输出解说正文、字幕短句、语气、预计朗读时长和特效建议。生成后检查空文本、重复度、敏感占位符和时长，超长内容优先压缩而不是简单截断。',
'导演评审会由事实、剧情、节奏和受众四个角色独立评价，再由总导演汇总共识与分歧。评审使用当前实际可运行模型，历史记录中的云端模型名只用于追溯。评审结论不自动修改时间线，用户确认后才形成新脚本版本。']),
('4.5 配音、字幕与视频渲染',[
'Piper按段生成WAV，保存音色、语速、情绪和音高参数。系统测量实际音频长度，并与分镜可用时长比较；超出时通过文案压缩、语速微调或延长镜头解决。字幕由同一脚本版本生成，避免文字、配音和画面来源不一致。',
'渲染器把源视频区间、配音、原声、BGM、字幕、贴图和转场转换为FFmpeg滤镜图。设备支持时选择NVENC，否则使用软件编码。输出完成后再次调用ffprobe检查时长、视频流和音频流，并生成校验指纹，只有通过验证的文件才进入下载和预览接口。'])]),
('第五章 三端功能实现',[
('5.1 Web端实现',[
'Web端是主要创作工作台，包含首页、任务创建、任务进度、片段复核、脚本编辑、素材库、时间线、成片预览、AI设置和诊断页面。首页突出当前项目和下一步操作，高级模型参数与授权信息集中在设置区域。',
'任务页持续展示九阶段状态和子进度，失败卡片给出解决建议与追踪号。片段和脚本页面保留自动证据、版本切换与人工编辑入口；预览页面使用浏览器原生播放器验证最终MP4，并提供字幕与文件导出。']),
('5.2 Windows EXE实现',[
'Windows Launcher读取安装配置，检查Java、FFmpeg、Ollama和后端端口，启动服务并等待健康接口成功后打开Web页面。安装器允许把程序、模型和数据放在G盘、F盘等非系统盘，避免大型模型持续占用C盘。',
'关闭应用时，Launcher请求后端保存任务检查点并安全停止；若后端超时，再终止由本次启动登记的进程。启动流程对端口占用给出具体进程提示，从而解决重复运行开发服务器造成的5173端口冲突。']),
('5.3 Android端实现',[
'Android端提供移动任务列表、任务详情、进度查看、成片预览、模型能力检查和服务器设置。它连接同一后端并读取持久化状态，因此可以在电脑执行长任务，在手机上查看结果。',
'界面采用适合窄屏的纵向卡片和底部导航。网络不可用时保留最后一次成功数据并显示重试；文件选择使用系统文档选择器，不扫描全部存储。发布验收需要在真实设备上覆盖权限、横竖屏、后台恢复和不同Android版本。']),
('5.4 三端协同关系',[
'三端共享后端任务与项目数据，但承担不同职责：Web负责完整编辑，EXE负责本地环境启动和退出，Android负责移动查看与轻量操作。该划分避免在每个平台重复实现全部专业功能。',
'接口响应使用统一状态枚举、时间格式和错误结构。任何入口执行取消、重试或版本切换后，其他入口再次读取状态即可得到一致结果。对于尚未支持的操作，移动端明确跳转到Web或给出说明，而不是展示没有实际作用的按钮。'])]),
('第六章 数据库、安全与可靠性设计',[
('6.1 数据库结构与Flyway迁移',[
'核心表围绕VIDEO_TASK展开，STAGE记录阶段状态，ARTIFACT登记产物，SCRIPT_REVISION和TIMELINE_REVISION保存版本，ASSET与PLACEMENT描述素材及其时间线位置，EXPORT记录成片快照。主键使用UUID或稳定标识，常用任务状态、更新时间和项目外键建立索引。',
'Flyway按版本顺序演进数据库。诊断程序在查询迁移历史前先判断历史表是否存在，并针对H2与PostgreSQL的布尔值和保留字差异执行兼容查询，避免简单使用success=TRUE在未初始化环境中报告错误SQL。']),
('6.2 本地存储与路径安全',[
'所有工作文件位于配置的存储根目录，任务使用独立UUID子目录。上传文件重命名后保存，数据库记录相对键；下载、预览、导出和删除均先规范化路径并检查其仍以根目录开头。临时文件与正式产物分开，校验成功后使用原子移动。',
'模型目录和媒体目录可配置到非系统盘。程序不会因为缺少C盘默认目录而重新下载同一模型；安装脚本在下载前检查目标空间、已有文件与校验值，并避免多个任务同时下载同一依赖。']),
('6.3 外部资源与授权控制',[
'素材模块记录来源、作者、许可、原始链接、下载时间和用途范围。能够访问的URL不代表可以用于作品，只有满足授权策略的素材才能自动进入时间线。Wikimedia等来源不可用时按类别记录原因并继续检查本地素材库。',
'远程下载限制HTTP协议、主机、重定向次数、文件大小和媒体类型，禁止访问本机、局域网及云元数据地址。账户Cookie与令牌只在请求上下文中使用，日志对Authorization、Cookie和敏感路径进行过滤。']),
('6.4 长任务恢复与诊断',[
'每个阶段完成后保存产物描述、文件大小、结构摘要和SHA-256指纹。恢复时依次验证前置产物，发现损坏就从最早失效阶段继续，而不是盲目信任数据库中的COMPLETED状态。外部进程设置最大运行时间并持续读取标准输出与错误输出，避免缓冲区阻塞。',
'诊断页检查数据库迁移、磁盘空间、FFmpeg、Whisper、Piper、Ollama、模型列表和前后端构建证据。红色表示核心失败，黄色表示可选能力或证据不足，绿色表示通过。云同步未启用在本地模式中只作为提醒，不阻塞视频处理。'])]),
('第七章 系统测试与结果分析',[
('7.1 测试环境与方法',[
'测试环境以Windows 64位、Java 21、Node.js、Maven Wrapper和Gradle Wrapper为基础。后端使用JUnit与Spring测试支持，前端使用Node Test和Vitest，构建阶段执行Vite生产编译。外部模型和FFmpeg在单元测试中使用可控替身，专项测试再连接真实程序。',
'测试采用等价类、边界值、状态迁移和故障注入方法。输入覆盖空文件、错误格式、超长路径、磁盘不足和重复任务；流程覆盖正常完成、模型404、进程超时、用户取消、应用重启和产物损坏；安全测试覆盖目录穿越与不安全远程地址。']),
('7.2 功能测试',[
'任务生命周期测试验证创建、排队、运行、完成、失败、取消和重试。模型测试验证已安装模型过滤、运行时模型与历史模型分离、结构化响应解析和导演评审失败提示。素材测试验证许可条件、来源降级、文件校验和放置关系。',
'三端人工测试按照创建任务、观察进度、修改脚本、预览成片的路径执行。Web端重点检查刷新恢复和错误覆盖层；EXE重点检查依赖、端口和安全退出；Android重点检查网络中断、窄屏布局与权限。']),
('7.3 自动化测试结果',[
'最近记录的完整Maven测试共337项，结果为0失败、0错误、4项环境跳过。跳过项需要特定外部程序或真实设备，因此报告中单独列出，不把跳过解释为通过。前端公共模块测试和Vite生产构建用于发现语法、模块引用及public资源导入问题。',
'项目新增快速检查脚本，在日常修改后先检查配置、JavaScript语法、关键资源路径和少量高价值测试。该方法缩短反馈时间，但数据库、任务恢复和渲染行为变化仍需要对应专项测试，发布前还需执行完整测试与设备验收。']),
('7.4 结果讨论',[
'测试证明系统的任务状态、产物管理、路径安全和异常处理在可控环境中符合设计。真实长视频的处理速度仍受素材时长、分辨率、显卡、模型大小和抽帧密度影响；成片质量还受到游戏类型、画面UI、解说风格和提示词影响。',
'因此本文不虚构统一准确率或固定实时率，而是把当前可复现的自动化结果、环境跳过和限制分别说明。后续应建立标准视频集，测量高光命中率、字幕时间误差、渲染实时率、显存峰值和用户文案修改比例。'])]),
('第八章 总结与展望',[
('8.1 总结',[
'本文完成了GameNarrator多模态游戏视频智能解说与自动剪辑系统的需求分析、架构设计、关键功能和三端入口实现。系统以九阶段可追踪流程连接媒体处理、语音识别、视觉理解、文案生成、语音合成、时间线和视频渲染，在本地设备形成从视频输入到MP4输出的闭环。',
'阶段检查点、产物指纹、取消与恢复机制提高了长任务可靠性；本地存储、相对路径和素材授权降低了数据风险；分镜、脚本和时间线版本为自动结果保留了人工修正入口。系统能够满足学年设计展示和初级创作者的基本需求。']),
('8.2 展望',[
'后续工作首先建立覆盖不同游戏、分辨率和声音条件的标准评测集；其次根据显存与任务类型自动选择模型量化、抽帧密度和编码器；再次统计真实功能使用率，将主流程进一步简化，同时保留专业模式。',
'在质量层面，可以引入多人盲评和A/B测试，评价高光选择、解说自然度、节奏和最终采用率；在工程层面，可完善Android设备矩阵、Windows干净安装验证、PostgreSQL生产迁移证据和可选的跨设备项目同步。'])])]

web_imgs=sorted(Path(ROOT/'docs'/'screenshots').glob('*.png')); dev_imgs=sorted(Path(ROOT/'docs'/'screenshots-device').glob('*.png'))
figures={('第二章 需求分析','2.2 功能需求'):web_imgs[0] if web_imgs else None,('第三章 系统总体设计','3.4 接口与状态交互'):web_imgs[1] if len(web_imgs)>1 else None,('第四章 关键技术与核心流程实现','4.2 语音转写与画面理解'):web_imgs[2] if len(web_imgs)>2 else None,('第四章 关键技术与核心流程实现','4.4 文案生成与导演评审'):web_imgs[3] if len(web_imgs)>3 else None,('第五章 三端功能实现','5.1 Web端实现'):web_imgs[4] if len(web_imgs)>4 else None,('第五章 三端功能实现','5.2 Windows EXE实现'):web_imgs[5] if len(web_imgs)>5 else None,('第五章 三端功能实现','5.3 Android端实现'):dev_imgs[0] if dev_imgs else None,('第六章 数据库、安全与可靠性设计','6.4 长任务恢复与诊断'):web_imgs[6] if len(web_imgs)>6 else None,('第七章 系统测试与结果分析','7.2 功能测试'):dev_imgs[1] if len(dev_imgs)>1 else None}

def set_cn(run,name='宋体',size=12,bold=False): run.font.name='Times New Roman';run._element.rPr.rFonts.set(qn('w:eastAsia'),name);run.font.size=Pt(size);run.bold=bold
def add_p(doc,text,first=True):
 p=doc.add_paragraph();p.alignment=WD_ALIGN_PARAGRAPH.JUSTIFY;p.paragraph_format.line_spacing=Pt(22);p.paragraph_format.first_line_indent=Pt(24) if first else Pt(0);set_cn(p.add_run(text));return p
def add_h(doc,text,lvl):
 p=doc.add_heading('',level=lvl);p.alignment=WD_ALIGN_PARAGRAPH.CENTER if lvl==1 else WD_ALIGN_PARAGRAPH.LEFT;set_cn(p.add_run(text),'黑体',16 if lvl==1 else 14,True);return p

doc=Document();sec=doc.sections[0];sec.page_height=Cm(29.7);sec.page_width=Cm(21);sec.top_margin=Cm(2.5);sec.bottom_margin=Cm(2.5);sec.left_margin=Cm(3);sec.right_margin=Cm(2.5)
for _ in range(3):doc.add_paragraph()
p=doc.add_paragraph();p.alignment=1;set_cn(p.add_run('2023级学年设计'), '黑体',22,True)
for _ in range(3):doc.add_paragraph()
p=doc.add_paragraph();p.alignment=1;set_cn(p.add_run(TITLE),'黑体',20,True)
for _ in range(4):doc.add_paragraph()
for t in ['院    别：信息工程学院','年级专业：2023级网络工程班','姓    名：陈华熠','学    号：20230862133','指导教师：张标汉        职称：【待填写】']:
 p=doc.add_paragraph();p.alignment=1;set_cn(p.add_run(t),'宋体',15)
doc.add_page_break();add_h(doc,TITLE,1);p=doc.add_paragraph();p.alignment=1;set_cn(p.add_run('陈华熠  20230862133  2023级网络工程班'),'宋体',12)
add_h(doc,'摘  要',1);add_p(doc,'针对游戏录屏剪辑流程长、专业工具学习成本高和云端服务存在上传、费用与隐私依赖等问题，本文设计并实现本地运行的多模态游戏视频智能解说与自动剪辑系统GameNarrator。系统采用Java 21、Spring Boot 3.2、Vue 3与Vite构建，以FFmpeg、whisper.cpp、Ollama Qwen2.5-VL和Piper完成媒体处理、语音识别、视觉理解、原创文案与中文配音。处理过程划分为九个可追踪阶段，并以产物指纹、断点恢复、失败重试和安全取消提高长任务可靠性。系统提供Web、Windows EXE和Android三端入口，模型与素材可保存在非系统盘。测试记录包含337项后端自动化测试，结果为0失败、0错误、4项环境跳过。实践表明，系统形成了从本地视频输入到可播放MP4输出的闭环，同时保留分镜、脚本和时间线的人工修正能力。')
add_p(doc,'关键词：多模态理解；游戏视频；自动剪辑；本地大模型；FFmpeg',False)
add_h(doc,'Abstract',1);add_p(doc,'This paper designs and implements GameNarrator, a locally deployed multimodal system for intelligent commentary and automatic editing of game videos. The system combines Spring Boot, Vue, FFmpeg, whisper.cpp, Qwen2.5-VL and Piper in a traceable nine-stage workflow. Artifact fingerprints, resumable checkpoints, bounded external processes and safe cancellation improve reliability. Web, Windows and Android clients share the same project and task data. The implemented system completes the path from source video to playable MP4 while retaining manual control over storyboards, scripts and timelines.')
add_p(doc,'Key words: multimodal understanding; game video; automatic editing; local model; FFmpeg',False)
doc.add_page_break();add_h(doc,'目  录',1)
for ch,secs in chapters:
 add_p(doc,ch,False)
 for h,_ in secs:add_p(doc,'    '+h,False)

fig_no=0
for ci,(ch,secs) in enumerate(chapters):
 doc.add_page_break();add_h(doc,ch,1)
 for sh,paras in secs:
  add_h(doc,sh,2)
  for text in paras:add_p(doc,text)
  img=figures.get((ch,sh))
  if img and img.exists():
   fig_no+=1;p=doc.add_paragraph();p.alignment=1;r=p.add_run();r.add_picture(str(img),width=Cm(13.5));p=doc.add_paragraph();p.alignment=1;set_cn(p.add_run(f'图{fig_no}  GameNarrator系统实际界面'),'宋体',10)
 if ch=='第三章 系统总体设计':
  add_h(doc,'3.5 核心实体关系说明',2);table=doc.add_table(rows=1,cols=4);table.style='Table Grid';hdr=table.rows[0].cells
  for c,t in zip(hdr,['实体','主要字段','关联对象','作用']):c.text=t
  for row in [('VideoTask','status、progress','Stage、Artifact','保存任务生命周期'),('ScriptRevision','parent、content','Storyboard、Voice','保存脚本版本'),('TimelineRevision','duration、snapshot','Placement、Export','固定渲染输入'),('Asset','license、source','Placement','管理素材与授权')]:
   cells=table.add_row().cells
   for c,t in zip(cells,row):c.text=t
 if ch=='第七章 系统测试与结果分析':
  add_h(doc,'7.5 测试结果汇总',2);table=doc.add_table(rows=1,cols=4);table.style='Table Grid'
  for c,t in zip(table.rows[0].cells,['检查项','结果','证据','说明']):c.text=t
  for row in [('Maven测试','通过','337项，0失败，0错误','4项环境跳过'),('前端构建','通过','Vite生产构建','检查模块与资源'),('快速检查','通过','check-fast.ps1','用于日常反馈'),('Android验收','待补充','真实设备证据','发布前完成')]:
   cells=table.add_row().cells
   for c,t in zip(cells,row):c.text=t

doc.add_page_break();add_h(doc,'参考文献',1)
refs=['[1] RADFORD A, et al. Robust Speech Recognition via Large-Scale Weak Supervision[C]. ICML, 2023.','[2] FFmpeg Developers. FFmpeg Documentation[EB/OL]. https://ffmpeg.org/documentation.html.','[3] Spring. Spring Boot Reference Documentation[EB/OL]. https://docs.spring.io/spring-boot/reference/.','[4] Vue.js. Vue 3 Guide[EB/OL]. https://vuejs.org/guide/.','[5] Ollama. Ollama Documentation[EB/OL]. https://docs.ollama.com/.','[6] Redgate. Flyway Documentation[EB/OL]. https://documentation.red-gate.com/flyway.','[7] rhasspy. Piper: A Fast Local Neural Text to Speech System[EB/OL].','[8] GB/T 7714—2015 信息与文献 参考文献著录规则[S].']
for r in refs:add_p(doc,r,False)
doc.save(DOCX)

font='CN';pdfmetrics.registerFont(TTFont(font,r'C:\Windows\Fonts\msyh.ttc'))
styles={'title':ParagraphStyle('title',fontName=font,fontSize=20,leading=30,alignment=TA_CENTER,spaceAfter=18),'h1':ParagraphStyle('h1',fontName=font,fontSize=16,leading=25,alignment=TA_CENTER,spaceBefore=8,spaceAfter=14),'h2':ParagraphStyle('h2',fontName=font,fontSize=14,leading=22,alignment=TA_LEFT,spaceBefore=10,spaceAfter=6),'body':ParagraphStyle('body',fontName=font,fontSize=11.5,leading=22,alignment=TA_JUSTIFY,firstLineIndent=24,spaceAfter=3),'small':ParagraphStyle('small',fontName=font,fontSize=9,leading=14,alignment=TA_CENTER)}
story=[Spacer(1,80),Paragraph('2023级学年设计',styles['title']),Spacer(1,80),Paragraph(TITLE,styles['title']),Spacer(1,100)]
for t in ['院    别：信息工程学院','年级专业：2023级网络工程班','姓    名：陈华熠','学    号：20230862133','指导教师：张标汉    职称：【待填写】']:story+=[Paragraph(t,styles['h2']),Spacer(1,8)]
story+=[PageBreak(),Paragraph(TITLE,styles['title']),Paragraph('摘  要',styles['h1']),Paragraph('针对游戏录屏剪辑流程长、专业工具学习成本高和云端服务存在上传、费用与隐私依赖等问题，本文设计并实现本地运行的多模态游戏视频智能解说与自动剪辑系统GameNarrator。系统采用Java 21、Spring Boot 3.2、Vue 3与Vite构建，以FFmpeg、whisper.cpp、Ollama Qwen2.5-VL和Piper完成媒体处理、语音识别、视觉理解、原创文案与中文配音。处理过程划分为九个可追踪阶段，并以产物指纹、断点恢复、失败重试和安全取消提高长任务可靠性。系统提供Web、Windows EXE和Android三端入口。测试记录包含337项后端自动化测试，结果为0失败、0错误、4项环境跳过。',styles['body']),Paragraph('关键词：多模态理解；游戏视频；自动剪辑；本地大模型；FFmpeg',styles['body']),Paragraph('Abstract',styles['h1']),Paragraph('This paper designs and implements GameNarrator, a locally deployed multimodal system for intelligent commentary and automatic editing of game videos. The system combines Spring Boot, Vue, FFmpeg, whisper.cpp, Qwen2.5-VL and Piper in a traceable nine-stage workflow while retaining manual control over storyboards, scripts and timelines.',styles['body'])]
story+=[PageBreak(),Paragraph('目  录',styles['h1'])]
for ch,secs in chapters:
 story.append(Paragraph(ch,styles['body']))
 for h,_ in secs:story.append(Paragraph('　　'+h,styles['body']))
fig_no=0
for ch,secs in chapters:
 story+=[PageBreak(),Paragraph(ch,styles['h1'])]
 for sh,paras in secs:
  story.append(Paragraph(sh,styles['h2']))
  for t in paras:story.append(Paragraph(t,styles['body']))
  img=figures.get((ch,sh))
  if img and img.exists():
   with PILImage.open(img) as im:w,h=im.size
   maxw,maxh=(250,410) if h>w else (390,240);scale=min(maxw/w,maxh/h);fig_no+=1
   story += [Spacer(1,5),Image(str(img),w*scale,h*scale),Paragraph(f'图{fig_no}  GameNarrator系统实际界面',styles['small']),Spacer(1,6)]
 if ch=='第三章 系统总体设计':
  story += [Paragraph('3.5 核心实体关系说明',styles['h2']),Table([['实体','主要字段','关联对象','作用'],['VideoTask','status、progress','Stage、Artifact','任务生命周期'],['ScriptRevision','parent、content','Storyboard、Voice','脚本版本'],['TimelineRevision','duration、snapshot','Placement、Export','固定渲染输入'],['Asset','license、source','Placement','素材与授权']],colWidths=[85,105,105,125],style=TableStyle([('FONTNAME',(0,0),(-1,-1),font),('FONTSIZE',(0,0),(-1,-1),9),('GRID',(0,0),(-1,-1),0.8,colors.black),('BACKGROUND',(0,0),(-1,0),colors.lightgrey),('VALIGN',(0,0),(-1,-1),'MIDDLE')]))]
 if ch=='第七章 系统测试与结果分析':
  story += [Paragraph('7.5 测试结果汇总',styles['h2']),Table([['检查项','结果','证据','说明'],['Maven测试','通过','337项，0失败，0错误','4项环境跳过'],['前端构建','通过','Vite生产构建','模块与资源'],['快速检查','通过','check-fast.ps1','日常反馈'],['Android验收','待补充','真实设备证据','发布前完成']],colWidths=[90,60,150,120],style=TableStyle([('FONTNAME',(0,0),(-1,-1),font),('FONTSIZE',(0,0),(-1,-1),9),('GRID',(0,0),(-1,-1),0.8,colors.black),('BACKGROUND',(0,0),(-1,0),colors.lightgrey)]))]
story+=[PageBreak(),Paragraph('参考文献',styles['h1'])]+[Paragraph(r,styles['body']) for r in refs]
def footer(canvas,doc): canvas.saveState();canvas.setFont(font,9);canvas.drawCentredString(A4[0]/2,25,str(doc.page));canvas.restoreState()
SimpleDocTemplate(str(PDF),pagesize=A4,rightMargin=70,leftMargin=85,topMargin=70,bottomMargin=55,title=TITLE,author='陈华熠').build(story,onFirstPage=footer,onLaterPages=footer)
import subprocess,re
pdfinfo=Path(r'C:\Users\longer\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\poppler\Library\bin\pdfinfo.exe')
info=subprocess.check_output([str(pdfinfo),str(PDF)],text=True,errors='ignore')
pages=int(re.search(r'^Pages:\s+(\d+)',info,re.M).group(1))
print(f'DOCX={DOCX}\nPDF={PDF}\nPAGES={pages}')
