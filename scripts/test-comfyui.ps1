$workflow = @{ 
  '1'=@{class_type='CheckpointLoaderSimple';inputs=@{ckpt_name='ChenkinNoob-XL-V0.5.safetensors'}}
  '2'=@{class_type='CLIPTextEncode';inputs=@{text='cute game character, colorful animation, clean composition';clip=@('1',1)}}
  '3'=@{class_type='CLIPTextEncode';inputs=@{text='blurry, low quality, watermark';clip=@('1',1)}}
  '4'=@{class_type='EmptyLatentImage';inputs=@{width=512;height=512;batch_size=1}}
  '5'=@{class_type='KSampler';inputs=@{seed=123456;steps=6;cfg=5.5;sampler_name='euler';scheduler='normal';denoise=1.0;model=@('1',0);positive=@('2',0);negative=@('3',0);latent_image=@('4',0)}}
  '6'=@{class_type='VAEDecode';inputs=@{samples=@('5',0);vae=@('1',2)}}
  '7'=@{class_type='SaveImage';inputs=@{filename_prefix='game-narrator-test';images=@('6',0)}}
}
$base='http://127.0.0.1:8188'; $client=[guid]::NewGuid().ToString(); $body=@{prompt=$workflow;client_id=$client}|ConvertTo-Json -Depth 12
$r=Invoke-RestMethod "$base/prompt" -Method Post -ContentType 'application/json' -Body $body; $id=$r.prompt_id; if(!$id){throw "ComfyUI 未返回 prompt_id: $($r|ConvertTo-Json)"}
for($i=0;$i -lt 180;$i++){Start-Sleep 1; $h=Invoke-RestMethod "$base/history/$id"; $entry=$h.$id; if($entry.outputs){$img=$entry.outputs.PSObject.Properties.Value|ForEach-Object{$_.images}|Where-Object{$_}|Select-Object -First 1; if($img){$u="$base/view?filename=$([uri]::EscapeDataString($img.filename))&subfolder=$([uri]::EscapeDataString($img.subfolder))&type=$($img.type)"; Invoke-WebRequest $u -OutFile 'artifacts/comfyui-test.png'; Write-Output "GENERATED=$((Get-Item artifacts/comfyui-test.png).Length)"; exit 0}}}
throw 'ComfyUI 生成超时'
