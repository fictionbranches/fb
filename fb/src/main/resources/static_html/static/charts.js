const labelData = $('#datacontainer').data('datestrings');
const inputData = $('#datacontainer').data('datecounts');

const cumData = (function(){
  let arr = [];
  let sum = 0;
  for (let x of inputData) {
    sum+=x;
    arr.push(sum);
  }
  return arr;
})();
const options = {
  width: 600,
  height: 400,
  axisY: {
    onlyInteger: true
  }
};
const labelSkip = Math.floor(labelData.length/8);
const responsiveOptions = [
  ['',{
    showPoint: false,
    axisX: {
      labelInterpolationFnc: function(value, index) {
        return index%labelSkip===0?value:false;
      }
    }
  }]
];

new Chartist.Line('#mainchart', {labels: labelData,series: [inputData]}, options, responsiveOptions);
new Chartist.Line('#cumchart', {labels: labelData,series: [cumData]}, options, responsiveOptions);
