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
$("#minus").onclick=()=>{state.temp=Math.max(16,state.temp-1);save()};$("#plus").onclick=()=>{state.temp=Math.min(30,state.temp+1);save()};$("#brand").onchange=e=>{state.brand=e.target.value;save();$("#hint").textContent=state.brand==="Sharp"?"Sharp cần model dàn lạnh/remote; web không phát IR":"Đã chọn "+state.brand+" · web không phát IR trực tiếp"};$("#power").onclick=()=>{state.power=!state.power;save();
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
