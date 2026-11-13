param(
    [string]$Source = 'F:\Desktop\GameNarrator学年设计答辩-三端扩充版.pptx',
    [string]$Output = (Join-Path $PSScriptRoot '..\docs\deliverables\GameNarrator学年设计答辩-汇报要求版.pptx')
)
$ErrorActionPreference='Stop'
$outputPath=[IO.Path]::GetFullPath($Output)
New-Item -ItemType Directory -Force -Path (Split-Path $outputPath) | Out-Null

function Find-TextShape($slide,[scriptblock]$predicate){
    foreach($shape in $slide.Shapes){
        if($shape.HasTextFrame -and $shape.TextFrame.HasText){
            $value=$shape.TextFrame.TextRange.Text.Trim()
            if(& $predicate $value){return $shape}
        }
    }
    return $null
}
function Set-Title($slide,[string]$text){
    $shape=Find-TextShape $slide {param($v) $v -match '^\d{2}\s' -or $v -in @('汇报目录','谢谢观看')}
    if(-not $shape){throw "Title shape not found on slide $($slide.SlideIndex)"}
    $shape.TextFrame.TextRange.Text=$text
}
function Set-Body($slide,[string[]]$items){
    $shape=Find-TextShape $slide {param($v) $v.Contains('■')}
    if(-not $shape){throw "Body shape not found on slide $($slide.SlideIndex)"}
    $shape.TextFrame.TextRange.Text=($items|ForEach-Object{"■ $_"}) -join "`r`n"
    $shape.TextFrame.TextRange.Font.Name='Microsoft YaHei'
    $shape.TextFrame.TextRange.Font.Size=17
    $shape.TextFrame.TextRange.ParagraphFormat.SpaceAfter=10
}

$ppt=New-Object -ComObject PowerPoint.Application
try{
    $presentation=$ppt.Presentations.Open($Source,$false,$false,$false)
    $presentation.SaveAs($outputPath,24)
    $slidesByOriginal=@{}
    foreach($number in @(1,2,3,4,5,6,8,9,10,14,15,18,20,24,25,26,27,28,29,30,31)){
        $slidesByOriginal[$number]=$presentation.Slides.Item($number).SlideID
    }

    $significance=$presentation.Slides.FindBySlideID($slidesByOriginal[3]).Duplicate().Item(1)
    Set-Title $significance '02 项目意义'
    Set-Body $significance @(
        '应用价值：把找高光、写文案、配音和渲染串成一条可操作流程，降低视频创作门槛。',
        '技术价值：综合实践多模态理解、任务编排、媒体处理、数据库和跨端开发。',
        '隐私价值：视频、模型和中间产物可保存在本地非系统盘，减少素材外传。',
        '工程价值：通过检查点、追踪号和诊断机制提升本地AI长任务的可恢复性。'
    )
    $environment=$presentation.Slides.FindBySlideID($slidesByOriginal[3]).Duplicate().Item(1)
    Set-Title $environment '03 开发环境'
    Set-Body $environment @(
        '操作系统：Windows 64位；主要开发语言：Java 21、JavaScript、Kotlin与C#。',
        '开发工具：IntelliJ IDEA/VS Code、Maven、Gradle、Node.js、Git。',
        '运行基线：16 GB内存、RTX 4060 8 GB或同等级显卡、至少30 GB可用空间。',
        '外部环境：FFmpeg、Ollama、whisper.cpp、Piper均在本机运行，路径可配置。'
    )

    $keepIds=@($slidesByOriginal[1],$slidesByOriginal[2],$slidesByOriginal[3],$significance.SlideID,$environment.SlideID,$slidesByOriginal[5],$slidesByOriginal[4],$slidesByOriginal[6],$slidesByOriginal[8],$slidesByOriginal[9],$slidesByOriginal[10],$slidesByOriginal[14],$slidesByOriginal[15],$slidesByOriginal[18],$slidesByOriginal[20],$slidesByOriginal[24],$slidesByOriginal[25],$slidesByOriginal[26],$slidesByOriginal[27],$slidesByOriginal[28],$slidesByOriginal[29],$slidesByOriginal[30],$slidesByOriginal[31])
    for($i=$presentation.Slides.Count;$i -ge 1;$i--){
        if($keepIds -notcontains $presentation.Slides.Item($i).SlideID){$presentation.Slides.Item($i).Delete()}
    }
    for($i=0;$i -lt $keepIds.Count;$i++){$presentation.Slides.FindBySlideID($keepIds[$i]).MoveTo($i+1)}

    $outline=$presentation.Slides.Item(2)
    Set-Body $outline @(
        '01 项目背景与意义',
        '02 开发环境、技术栈与系统分析',
        '03 系统功能实现',
        '04 可靠性、数据设计与测试',
        '05 项目总结与展望'
    )
    Set-Title $presentation.Slides.Item(3) '01 项目背景与问题定义'
    Set-Title $presentation.Slides.Item(6) '04 开发技术栈与总体架构'
    Set-Body $presentation.Slides.Item(6) @(
        '前端：Vue 3 + Vite，构建浏览器创作工作台。',
        '后端：Java 21 + Spring Boot 3.2，提供REST接口、任务编排和权限控制。',
        '智能与媒体：Ollama/Qwen2.5-VL、whisper.cpp、Piper和FFmpeg。',
        '数据与客户端：JPA + Flyway + H2/PostgreSQL，配套.NET启动器与Android应用。'
    )
    Set-Title $presentation.Slides.Item(7) '05 系统需求分析'
    Set-Body $presentation.Slides.Item(7) @(
        '普通创作者：快速导入视频、选择风格、查看进度并导出成片。',
        '进阶创作者：修改高光、文案、配音、字幕、素材和时间线。',
        '维护人员：检查模型、磁盘、数据库迁移、日志和发布状态。',
        '非功能需求：本地隐私、任务可恢复、错误可追踪、路径安全和配置可替换。'
    )
    Set-Title $presentation.Slides.Item(8) '06 系统流程分析：九阶段工作流'
    Set-Title $presentation.Slides.Item(9) '07 功能实现：Web任务创建'
    Set-Title $presentation.Slides.Item(10) '08 功能实现：Web专业工具'
    Set-Title $presentation.Slides.Item(11) '09 功能实现：Web项目编辑'
    Set-Title $presentation.Slides.Item(12) '10 功能实现：Windows EXE入口'
    Set-Body $presentation.Slides.Item(12) @(
        '为不熟悉命令行的Windows用户提供统一启动入口。',
        '检查Java、FFmpeg、模型服务、端口和数据目录后再启动后端。',
        '健康检查通过后打开本机Web工作台，失败时展示可执行原因。',
        '程序、模型、data和storage分离，支持把大型文件部署到非C盘。'
    )
    Set-Title $presentation.Slides.Item(13) '11 功能实现：EXE启动与退出链路'
    Set-Title $presentation.Slides.Item(14) '12 功能实现：Android移动工作台'
    Set-Title $presentation.Slides.Item(15) '13 功能实现：Android能力检查'
    Set-Title $presentation.Slides.Item(16) '14 功能实现：多模态分析与高光筛选'
    Set-Title $presentation.Slides.Item(17) '15 功能实现：文案、配音与视频渲染'
    Set-Title $presentation.Slides.Item(18) '16 系统可靠性与安全设计'
    Set-Title $presentation.Slides.Item(19) '17 数据库与版本管理'
    Set-Title $presentation.Slides.Item(20) '18 系统测试与验证结果'
    Set-Title $presentation.Slides.Item(21) '19 项目特点、局限与展望'
    Set-Title $presentation.Slides.Item(22) '20 总结与演示流程'

    foreach($slide in $presentation.Slides){
        foreach($shape in $slide.Shapes){
            if($shape.HasTextFrame -and $shape.TextFrame.HasText -and $shape.TextFrame.TextRange.Text -match '^\d{2}$'){
                $shape.TextFrame.TextRange.Text=([string]$slide.SlideIndex).PadLeft(2,'0')
            }
        }
    }
    $presentation.Save()
    $pdf=[IO.Path]::ChangeExtension($outputPath,'.pdf')
    $presentation.SaveAs($pdf,32)
    $count=$presentation.Slides.Count
    $presentation.Close()
}finally{$ppt.Quit()}
"PPTX=$outputPath`nPDF=$pdf`nSLIDES=$count"
