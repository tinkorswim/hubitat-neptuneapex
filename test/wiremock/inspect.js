
const fs = require('fs/promises');
const path = require('path');
const { exit } = require('process');

function summary(devices){
    const deviceTypes = devices.reduce((summary, output)=>{
        summary[output.type]=summary[output.type]||[]
        summary[output.type].push(output)
        return summary
    },{});

    return `${Object.keys(deviceTypes).map(x=>`${x}(${deviceTypes[x].length})`).join(", ")}`
}
async function go(){
    const filename = process.argv.slice(2)[0];
    if(!filename){
        console.log("filename required as first arg");
        exit(0);
    }
    const status = JSON.parse(await fs.readFile(filename,{ encoding: 'utf8' }));
    console.log(`modules(${status.modules.length})->${status.modules.map(m=>`${m.abaddr}-${m.hwtype}`).join(", ")}`);
    console.log(`inputs(${status.inputs.length})->${summary(status.inputs)}`);
    console.log(`outputs(${status.outputs.length})->${summary(status.outputs)}`);
};
go();
