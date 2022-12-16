const fs = require('fs/promises');
const path = require('path');
const crypto = require('crypto');

const REGEX_DRIVER_NAME = /definition\s*?\(.*?name:\s*(?:(?:""([^""]*)"")|(?:'([^']*)'))/gm;
const NAMESPACE="tinkorswim";
const MANIFEST_FILENAME="hpmManifest.json";
const BASE_GITHUB_URL="https://github.com/tinkorswim/hubitat-neptuneapex/blob/";

console.log("creating hubitat package manager manifest")


async function newEntry(path,file,location,version){  
  const content = await fs.readFile(path+file,{ encoding: 'utf8' });
  let name = [...content.matchAll(REGEX_DRIVER_NAME)][0][2]
  const entry=
  {
    "id": crypto.randomUUID(),
    "name": name,
    "namespace": NAMESPACE,
    "location": location,
    "required": true,
    "version": version
  }
  return entry
}

async function processFiles(type,manifest,newVersion){
  const dir = path.join(__dirname, type);
  const files = await fs.readdir(dir, { withFileTypes: true });
  for(const file of files){
    const location = `${BASE_GITHUB_URL}${manifest.version}/${type}/${file.name}`;
    const existing = manifest[type].find(d=>d.location===location);
    if(!existing){
      manifest[type].push(await newEntry(dir+"/",file.name,location,newVersion));
    }
    else{
      const newLocation = `${BASE_GITHUB_URL}${newVersion}/${type}/${file.name}`;
      existing.location=newLocation;
      existing.version=newVersion;
    }
  }
  return manifest;
}

async function createManifest(){
  try {
    const newVersion = await fs.readFile("VERSION","utf-8");
    const manifest = JSON.parse(await fs.readFile(MANIFEST_FILENAME,"utf-8"));
    const newManifest = await processFiles("drivers",manifest,newVersion);
    if(newManifest){
      newManifest.version=newVersion;
      await fs.writeFile(MANIFEST_FILENAME, JSON.stringify(newManifest,null,2));
    }    
  }
  catch(err){
    console.log(err)
  }
};
createManifest()
