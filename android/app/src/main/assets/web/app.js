const $=s=>document.querySelector(s);
const defaults={power:false,temp:24,mode:"COOL",fan:"AUTO",brand:"Daikin",accent:"ocean",fontSize:"normal"};
const state={...defaults};
try{Object.assign(state,JSON.parse(localStorage.getItem("daikin-state")||"{}"))}catch{}
function save(){localStorage.setItem("daikin-state",JSON.stringify(state));render()}
const fontScale={small:0.9,normal:1,large:1.15};
const accentMeta={ocean:{theme:"#eaf7fa"},mint:{theme:"#eaf8f0"},lavender:{theme:"#f3effb"},rose:{theme:"#fff0f4"},amber:{theme:"#fff7e8"}};
function applyAppearance(){
  const accent=accentMeta[state.accent]?state.accent:"ocean";
  const size=fontScale[state.fontSize]?state.fontSize:"normal";
  state.accent=accent;state.fontSize=size;
  document.documentElement.dataset.accent=accent;
  document.documentElement.style.setProperty("--font-scale",fontScale[size]);
  const meta=document.querySelector('meta[name="theme-color"]');if(meta)meta.content=accentMeta[accent].theme;
  document.querySelectorAll("[data-accent]").forEach(b=>{const selected=b.dataset.accent===accent;b.classList.toggle("selected",selected);b.setAttribute("aria-pressed",String(selected))});
  document.querySelectorAll("[data-font]").forEach(b=>{const selected=b.dataset.font===size;b.classList.toggle("selected",selected);b.setAttribute("aria-pressed",String(selected))});
}
function render(){
  $("#brand").value=state.brand||"Daikin";$("#temp").textContent=state.temp;
  $("#status").textContent=state.power?`Đang bật · ${{COOL:"Làm lạnh",DRY:"Hút ẩm",FAN:"Quạt",HEAT:"Sưởi"}[state.mode]}`:"Máy lạnh đang tắt";
  $("#powerText").textContent=state.power?"Tắt máy lạnh":"Bật máy lạnh";$("#power").classList.toggle("off",!state.power);$("#power").setAttribute("aria-pressed",String(state.power));$("#power").setAttribute("aria-label",state.power?"Tắt máy lạnh":"Bật máy lạnh");
  $("#trackfill").style.width=((state.temp-16)/14*100)+"%";
  document.querySelectorAll("[data-mode]").forEach(b=>{const selected=b.dataset.mode===state.mode;b.classList.toggle("selected",selected);b.setAttribute("aria-pressed",String(selected))});
  document.querySelectorAll("[data-fan]").forEach(b=>{const selected=b.dataset.fan===state.fan;b.classList.toggle("selected",selected);b.setAttribute("aria-pressed",String(selected))});
  applyAppearance();
}
$("#minus").onclick=()=>{state.temp=Math.max(16,state.temp-1);save()};$("#plus").onclick=()=>{state.temp=Math.min(30,state.temp+1);save()};$("#brand").onchange=e=>{state.brand=e.target.value;save();if(window.AndroidACRemote&&typeof window.AndroidACRemote.setBrand==="function"){try{window.AndroidACRemote.setBrand(state.brand)}catch{}}$("#hint").textContent="Đã chọn "+state.brand+(window.AndroidACRemote?" · nhấn Kiểm tra IR để xem tần số tham khảo":" · web không phát IR trực tiếp")};$("#power").onclick=()=>{state.power=!state.power;save();
  if(window.AndroidACRemote && typeof window.AndroidACRemote.sendState === "function"){
    try { const result=window.AndroidACRemote.sendState(JSON.stringify(state)); $("#hint").textContent=result || "Đã gửi yêu cầu tới bộ phát IR"; }
    catch(e){ $("#hint").textContent="Không thể gửi lệnh IR trên thiết bị này."; }
  } else { $("#hint").textContent="Đã cập nhật giao diện · web không gửi lệnh IR"; }
};
document.querySelectorAll("[data-mode]").forEach(b=>b.onclick=()=>{state.mode=b.dataset.mode;save()});document.querySelectorAll("[data-fan]").forEach(b=>b.onclick=()=>{state.fan=b.dataset.fan;save()});
let deferred;
const installButton=$("#install");
const isStandalone=window.matchMedia("(display-mode: standalone)").matches || window.navigator.standalone===true;
if(isStandalone){installButton.hidden=true;}
window.addEventListener("beforeinstallprompt",e=>{e.preventDefault();deferred=e;installButton.hidden=false;installButton.textContent="Cài ứng dụng";});
installButton.onclick=async()=>{
  if(deferred){deferred.prompt();await deferred.userChoice;deferred=null;installButton.hidden=true;return;}
  $("#hint").textContent="Mở menu ⋮ của Chrome rồi chọn Cài đặt ứng dụng hoặc Thêm vào màn hình chính.";
};
window.addEventListener("appinstalled",()=>{installButton.hidden=true;$("#hint").textContent="Đã cài ứng dụng vào thiết bị.";});
if("serviceWorker"in navigator && location.protocol!=="file:")navigator.serviceWorker.register("./sw.js").catch(()=>{});
const irCheckButton=$("#checkIr");
if(irCheckButton){irCheckButton.onclick=()=>{
  if(window.AndroidACRemote && typeof window.AndroidACRemote.checkIr === "function"){
    try{$("#hint").textContent=window.AndroidACRemote.checkIr() || "Không có kết quả kiểm tra IR.";}
    catch(e){$("#hint").textContent="Lỗi khi kiểm tra IR trên Android.";}
  }else{$("#hint").textContent="Kiểm tra bộ phát IR chỉ khả dụng trong ứng dụng Android; trình duyệt web không truy cập được phần cứng IR.";}
};}
render();

// Appearance settings are saved alongside the remote preferences.
document.querySelectorAll("[data-accent]").forEach(button=>button.addEventListener("click",()=>{state.accent=button.dataset.accent;save()}));
document.querySelectorAll("[data-font]").forEach(button=>button.addEventListener("click",()=>{state.fontSize=button.dataset.font;save()}));
$("#resetAppearance").addEventListener("click",()=>{state.accent=defaults.accent;state.fontSize=defaults.fontSize;save()});


// IR learning assistant: phone-only capture is intentionally not simulated.
const learnedStorageKey="universal-ac-learned-ir-v1";
let learnedCommands=[];
try{learnedCommands=JSON.parse(localStorage.getItem(learnedStorageKey)||"[]");if(!Array.isArray(learnedCommands))learnedCommands=[];}catch{learnedCommands=[];}
const learnResult=$("#learnResult");
function setLearnResult(message){if(learnResult)learnResult.textContent=message;}
function parseRawPattern(){
  const text=$("#learnRaw").value.trim();
  if(!text)throw new Error("Hãy dán dữ liệu dạng sóng thô trước.");
  const values=text.split(/[\s,;]+/).filter(Boolean).map(v=>Number(v));
  if(values.length<6)throw new Error("Dữ liệu quá ngắn; cần ít nhất 6 khoảng mark/space.");
  if(values.length>1000)throw new Error("Dữ liệu quá dài (tối đa 1000 khoảng thời gian).");
  if(values.some(v=>!Number.isFinite(v)||!Number.isInteger(v)||v<=0||v>1000000))throw new Error("Mỗi khoảng thời gian phải là số nguyên dương không quá 1.000.000 micro giây.");
  return values;
}
function guessProtocol(p){
  // Heuristic only: timing signatures can suggest NEC/Sony; this is not a full decoder.
  if(p.length>=65 && p.length<=70 && p[0]>=7500&&p[0]<=10000&&p[1]>=3500&&p[1]<=5500){
    const marks=p.filter((_,i)=>i%2===0).slice(1);
    const spaces=p.filter((_,i)=>i%2===1).slice(1);
    const markRatio=marks.filter(x=>x>=350&&x<=800).length/Math.max(1,marks.length);
    const spaceRatio=spaces.filter(x=>(x>=350&&x<=850)||(x>=1300&&x<=1900)).length/Math.max(1,spaces.length);
    if(markRatio>.75&&spaceRatio>.75)return "NEC (ước đoán theo thời gian)";
  }
  if(p.length>=20&&p.length<=45&&p[0]>=1800&&p[0]<=3000&&p[1]>=400&&p[1]<=1000)return "Sony SIRC (ước đoán theo thời gian)";
  return "Chưa nhận diện — dạng sóng thô";
}
function renderLearned(){
  const host=$("#learnedList");if(!host)return;host.replaceChildren();
  if(!learnedCommands.length){const p=document.createElement("p");p.className="learn-help";p.textContent="Chưa có lệnh nào được lưu.";host.append(p);return;}
  learnedCommands.forEach((item,index)=>{
    const row=document.createElement("div");row.className="learned-item";
    const meta=document.createElement("div");meta.className="learned-meta";
    const name=document.createElement("strong");name.textContent=item.name;
    const detail=document.createElement("small");detail.textContent=`${item.frequency} Hz · ${item.protocol} · ${item.pattern.length} khoảng`;
    meta.append(name,detail);
    const play=document.createElement("button");play.type="button";play.className="saved-play";play.textContent="Phát";play.addEventListener("click",()=>{
      if(!window.AndroidACRemote||typeof window.AndroidACRemote.transmitLearned!="function"){setLearnResult("Phát lại chỉ khả dụng trong ứng dụng Android có bộ phát IR. Web/PWA không phát IR.");return;}
      try{setLearnResult(window.AndroidACRemote.transmitLearned(JSON.stringify(item))||"Đã gửi yêu cầu phát lại.");}catch(e){setLearnResult("Không phát được: "+e.message);}
    });
    const del=document.createElement("button");del.type="button";del.className="saved-delete";del.textContent="Xóa";del.addEventListener("click",()=>{learnedCommands.splice(index,1);localStorage.setItem(learnedStorageKey,JSON.stringify(learnedCommands));renderLearned();});
    row.append(meta,play,del);host.append(row);
  });
}
// BLE/GATT learner integration. Hardware-specific payload formats remain explicit, never guessed.
const bleStatusEl=$("#bleStatus"), bleDevicesEl=$("#bleDevices"), bleServicesEl=$("#bleServices"), bleCapturePreview=$("#bleCapturePreview");
function bleStatus(text){if(bleStatusEl)bleStatusEl.textContent=text;}
function refreshBleDevices(){
  if(!window.AndroidACRemote||!AndroidACRemote.getBleDevices)return;
  try{const result=JSON.parse(AndroidACRemote.getBleDevices());bleStatus(result.status||"BLE");if(!bleDevicesEl)return;bleDevicesEl.replaceChildren();
    const devices=result.devices||[];if(!devices.length){const p=document.createElement("p");p.className="learn-help";p.textContent="Chưa thấy thiết bị BLE. Đảm bảo bộ học IR đang bật, ở gần và hỗ trợ BLE/GATT.";bleDevicesEl.append(p);return;}
    devices.forEach(device=>{const row=document.createElement("div");row.className="learned-item";const meta=document.createElement("div");meta.className="learned-meta";const name=document.createElement("strong");name.textContent=device.name||"BLE device";const address=document.createElement("small");address.textContent=device.address;meta.append(name,address);const connect=document.createElement("button");connect.className="saved-play";connect.type="button";connect.textContent="Kết nối";connect.addEventListener("click",()=>{try{bleStatus(AndroidACRemote.connectBle(device.address));setTimeout(refreshBleServices,1200);setTimeout(refreshBleServices,3000);}catch(e){bleStatus("Kết nối lỗi: "+e.message);}});row.append(meta,connect);bleDevicesEl.append(row);});
  }catch(e){bleStatus("Không đọc được danh sách BLE: "+e.message);}
}
function refreshBleServices(){
  if(!window.AndroidACRemote||!AndroidACRemote.getBleServices)return;
  try{const result=JSON.parse(AndroidACRemote.getBleServices());bleStatus(result.status||"BLE");if(!bleServicesEl)return;bleServicesEl.replaceChildren();
    (result.services||[]).forEach(service=>{const heading=document.createElement("p");heading.className="learn-help";heading.textContent="Service: "+service.uuid;bleServicesEl.append(heading);
      (service.characteristics||[]).forEach(ch=>{const row=document.createElement("div");row.className="learned-item";const meta=document.createElement("div");meta.className="learned-meta";const id=document.createElement("small");id.textContent=ch.uuid;const props=document.createElement("small");props.textContent=ch.properties||"Không rõ thuộc tính";meta.append(id,props);const choose=document.createElement("button");choose.type="button";choose.className="saved-play";choose.textContent="Chọn UUID";choose.addEventListener("click",()=>{$("#bleCharacteristicUuid").value=ch.uuid;});row.append(meta,choose);bleServicesEl.append(row);});
    });if(!(result.services||[]).length){const p=document.createElement("p");p.className="learn-help";p.textContent="Chưa có dịch vụ. Đợi kết nối và đọc GATT hoàn tất.";bleServicesEl.append(p);}
  }catch(e){bleStatus("Không đọc được dịch vụ GATT: "+e.message);}
}
function refreshBleCapture(){
  if(!window.AndroidACRemote||!AndroidACRemote.getLatestBleCapture)return;
  try{const c=JSON.parse(AndroidACRemote.getLatestBleCapture()||"{}");if(!c.hex){if(bleCapturePreview)bleCapturePreview.textContent="Chưa nhận dữ liệu từ BLE.";return;}
    const summary=`Đã nhận payload BLE · ${c.hex.length/2} byte · UUID ${c.characteristicUuid||"?"} · HEX: ${c.hex.slice(0,240)}${c.hex.length>240?"…":""}${c.text?" · Text: "+c.text.slice(0,180):""}`;
    if(bleCapturePreview)bleCapturePreview.textContent=summary;
    if(c.pattern&&Array.isArray(c.pattern)){if(bleCapturePreview)bleCapturePreview.textContent+=` · Pattern ${c.pattern.length} khoảng, ${c.frequency||38000} Hz`;}
  }catch(e){if(bleCapturePreview)bleCapturePreview.textContent="Lỗi đọc payload: "+e.message;}
}
const scanBleButton=$("#scanBle");if(scanBleButton)scanBleButton.addEventListener("click",()=>{if(!window.AndroidACRemote||!AndroidACRemote.scanBleDevices){bleStatus("Quét BLE chỉ khả dụng trong ứng dụng Android.");return;}try{bleStatus(AndroidACRemote.scanBleDevices());setTimeout(refreshBleDevices,1500);setTimeout(refreshBleDevices,4000);setTimeout(refreshBleDevices,8000);setTimeout(refreshBleDevices,12500);}catch(e){bleStatus("Quét BLE lỗi: "+e.message);}});
const disconnectBleButton=$("#disconnectBle");if(disconnectBleButton)disconnectBleButton.addEventListener("click",()=>{try{bleStatus(AndroidACRemote.disconnectBle());}catch(e){bleStatus(e.message);}});
const subscribeBleButton=$("#subscribeBle");if(subscribeBleButton)subscribeBleButton.addEventListener("click",()=>{const uuid=$("#bleCharacteristicUuid").value.trim();if(!uuid){bleStatus("Chọn hoặc nhập UUID characteristic trước.");return;}try{bleStatus(AndroidACRemote.subscribeBleCharacteristic(uuid));}catch(e){bleStatus("Không đăng ký được: "+e.message);}});
const refreshBleCaptureButton=$("#refreshBleCapture");if(refreshBleCaptureButton)refreshBleCaptureButton.addEventListener("click",refreshBleCapture);
const importBleCaptureButton=$("#importBleCapture");if(importBleCaptureButton)importBleCaptureButton.addEventListener("click",()=>{try{const imported=JSON.parse(AndroidACRemote.importBleCaptureToRaw());if(!imported.pattern)throw new Error("Payload BLE chưa có dạng sóng mark/space được nhận diện rõ ràng.");$("#learnRaw").value=imported.pattern.join(",");$("#learnFrequency").value=imported.frequency||38000;$("#learnProtocol").value=imported.protocol||"Chưa nhận diện";setLearnResult("Đã nhập pattern từ payload BLE. Hãy phân tích và kiểm tra định dạng/tần số theo tài liệu bộ học IR trước khi phát.");}catch(e){setLearnResult("Chưa thể nhập dạng sóng: "+e.message);}});
setInterval(()=>{refreshBleDevices();refreshBleServices();refreshBleCapture();},2500);

const captureButton=$("#captureIr");if(captureButton)captureButton.addEventListener("click",()=>{
  if(window.AndroidACRemote&&typeof window.AndroidACRemote.startIrCapture==="function"){
    try{setLearnResult(window.AndroidACRemote.startIrCapture());}catch(e){setLearnResult("Không thể bắt tín hiệu: "+e.message);}
  }else setLearnResult("Không thể bắt IR bằng riêng Xiaomi 17 qua API Android phổ thông. Dán dạng sóng thô đã có để phân tích/lưu, hoặc cần bộ thu IR rời để thu thật.");
});
const analyzeButton=$("#analyzeIr");if(analyzeButton)analyzeButton.addEventListener("click",()=>{
  try{const p=parseRawPattern();const protocol=guessProtocol(p);$("#learnProtocol").value=protocol;const duration=p.reduce((a,b)=>a+b,0);setLearnResult(`Đã phân tích ${p.length} khoảng thời gian; tổng ${duration} µs (${(duration/1000).toFixed(1)} ms). Kết quả giao thức: ${protocol}. Nhận diện theo mẫu thời gian chỉ là ước đoán, không xác minh hãng/model.`);}catch(e){setLearnResult(e.message);}
});
const saveButton=$("#saveLearnedIr");if(saveButton)saveButton.addEventListener("click",()=>{
  try{const pattern=parseRawPattern();const frequency=Number($("#learnFrequency").value);if(!Number.isInteger(frequency)||frequency<20000||frequency>60000)throw new Error("Tần số phải trong khoảng 20.000–60.000 Hz.");const name=$("#learnName").value.trim()||"Lệnh IR";const protocol=$("#learnProtocol").value||guessProtocol(pattern);const total=pattern.reduce((a,b)=>a+b,0);if(total>2000000)throw new Error("Dạng sóng dài hơn 2 giây. Android ConsumerIrManager không phát mẫu dài như vậy trong một lần; không lưu như lệnh phát lại trực tiếp.");const item={id:Date.now().toString(36)+Math.random().toString(36).slice(2,7),name,frequency,protocol,pattern,createdAt:new Date().toISOString(),source:"manual-import-not-captured"};learnedCommands.unshift(item);localStorage.setItem(learnedStorageKey,JSON.stringify(learnedCommands));renderLearned();setLearnResult("Đã lưu lệnh nhập thủ công trên thiết bị này. Chưa phải tín hiệu được Xiaomi 17 tự bắt; hãy thử phát gần máy lạnh và xác nhận phản hồi thực tế.");}catch(e){setLearnResult(e.message);}
});
const clearButton=$("#clearLearnedIr");if(clearButton)clearButton.addEventListener("click",()=>{if(!learnedCommands.length){setLearnResult("Danh sách đang trống.");return;}if(confirm("Xóa tất cả lệnh IR đã lưu trên thiết bị này?")){learnedCommands=[];localStorage.setItem(learnedStorageKey,"[]");renderLearned();setLearnResult("Đã xóa danh sách lệnh IR đã lưu.");}});
renderLearned();
