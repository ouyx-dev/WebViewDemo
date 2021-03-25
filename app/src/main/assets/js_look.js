console.log("浏览时间"+look_time);
function toTop(target) {
  return function () {
    target.scrollIntoView({
      behavior: "smooth",
      block: "start"
    });
  };
}
/*生成从minNum到maxNum的随机数*/
function randomNum(minNum,maxNum){
    switch(arguments.length){
        case 1:
            return parseInt(Math.random()*minNum+1,10);
        break;
        case 2:
            return parseInt(Math.random()*(maxNum-minNum+1)+minNum,10);
        break;
            default:
                return 0;
            break;
    }
}

function toBottom(target) {
  return function () {
    target.scrollIntoView({
      behavior: "smooth",
      block: "end"
    });
  };
}

function finish(){
    console.log("finish");
  window.java_obj.finish();
}

var divs = document.getElementsByTagName("body")[0].getElementsByTagName("div");
console.log("\n divs.length= " + divs.length);
var step = 1;
var time=look_time/10;
if (divs.length > 10) {
  step = parseInt(divs.length / 10);
}
console.log("\n step= " + step);
for(i=0;i<divs.length;i=i+step){
  setTimeout(toTop(divs[i]),time);
  time+=(look_time/10);
  console.log("浏览 i="+i+",time="+time);
   if((i+step)>(divs.length-1)){
    setTimeout(finish,time);
   }
}

