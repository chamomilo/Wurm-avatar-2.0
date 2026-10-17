"""Recover approved pre-fairing torso detail without restoring joint-area ridges.

Run with the pinned Blender. The pre-fairing authoring bank is an explicit input;
the command updates Heavy and the muscular/full Build corner, retaining local cup
residuals and all other targets. Original, rigs, UVs, weights and clips stay exact.
"""
import sys, os, struct, json
from pathlib import Path
import numpy as np
import bpy
sys.path.insert(0,os.path.dirname(__file__))
from export_body_morphs import read_approved
from build_avatar_bodies import VARIANTS, PROJECT

def read(path):
    with open(path,'rb') as f:
        def integer(): return struct.unpack('>i',f.read(4))[0]
        def text(): return f.read(struct.unpack('>H',f.read(2))[0]).decode('ascii')
        def floats(n): return np.array(struct.unpack('>'+str(n)+'f',f.read(4*n))).reshape(-1,3)
        assert f.read(4)==b'AVS1'
        sha,body=text(),text();meshes=[]
        for _ in range(integer()):
            name,n=text(),integer();base,normals=floats(n),floats(n);targets={}
            for _ in range(integer()):
                key=text();targets[key]=(floats(n),floats(n))
            meshes.append(dict(name=name,base=base,normals=normals,targets=targets))
        assert not f.read(1)
    return sha,body,meshes

def write(path,sha,body,meshes):
    with open(path,'wb') as f:
        def text(v): b=v.encode('ascii');f.write(struct.pack('>H',len(b))+b)
        def integer(v): f.write(struct.pack('>i',v))
        def floats(v): f.write(np.asarray(v,dtype='>f4').tobytes())
        f.write(b'AVS1');text(sha);text(body);integer(len(meshes))
        for m in meshes:
            text(m['name']);integer(m['base'].size);floats(m['base']);floats(m['normals']);integer(len(m['targets']))
            for k,(p,n) in m['targets'].items(): text(k);floats(p);floats(n)

def smooth(v): v=np.clip(v,0,1);return v*v*(3-2*v)
def normals(points,triangles,previous,changed):
    data=bpy.data.meshes.new('Relief normals')
    data.from_pydata(points.tolist(),[],triangles.tolist())
    for polygon in data.polygons: polygon.use_smooth=True
    data.update();result=np.array([v.normal[:] for v in data.vertices])
    affected=np.zeros(len(points),dtype=bool)
    for tri in triangles:
        if changed[tri].any(): affected[tri]=True
    result[~affected]=previous[~affected];bpy.data.meshes.remove(data);return result

def main():
    args=sys.argv[sys.argv.index('--')+1:]
    previous=Path(args[0])
    baseline=Path(PROJECT)/'working/relief-0120-baseline'
    baseline.mkdir(parents=True,exist_ok=True)
    rows=[]
    for spec in VARIANTS:
        if not spec['proxy']: continue
        name=spec['name'];path=Path(PROJECT)/'assets/working/morphs'/f'{name}.avsrc'
        checkpoint=baseline/f'{name}.avsrc'
        if not checkpoint.exists():
            sha,body,current=read(path)
            # One-time recovery of the first trial; subsequent runs always start
            # from this immutable complete bank, so detail never accumulates.
            if len(args)>1:
                _,_,smoothed=read(Path(args[1])/f'{name}.avsrc')
                _,approved=read_approved(spec)
                for m,reference,topology in zip(current,smoothed,approved):
                    if 'Eyes' in m['name']: continue
                    applied=m['targets']['mpfb-heavy'][0]-reference['targets']['mpfb-heavy'][0]
                    for key in ['mpfb-heavy','build-full','build-breast-small-full','build-breast-large-full']:
                        if key not in m['targets']: continue
                        p,n=m['targets'][key];p=p-applied
                        m['targets'][key]=(p,normals(p,topology['triangles'],n,np.linalg.norm(applied,axis=1)>0))
            write(checkpoint,sha,body,current)
        sha,body,current=read(checkpoint);old_sha,old_body,old=read(previous/f'{name}.avsrc')
        assert sha==old_sha and body==old_body
        _,approved=read_approved(spec)
        for m,prior,topology in zip(current,old,approved):
            assert m['name']==prior['name'] and np.array_equal(m['base'],prior['base'])
            if 'Eyes' in m['name']: continue
            points=m['base'];height=np.max(points[:,1]);ratio=height/1.67
            x,y,z=points.T/ratio
            # Chest, abdomen and back only; stay away from armpit, neck and hip seams.
            mask=(smooth((y-.79)/.12)*smooth((1.43-y)/.12)*smooth((.16-np.abs(x))/.06)
                  * np.maximum(smooth((z-.04)/.05),smooth((y-1.10)/.12)))[:,None]
            detail=(prior['targets']['mpfb-heavy'][0]-m['targets']['mpfb-heavy'][0])*mask
            size=np.linalg.norm(detail,axis=1)
            detail*=np.minimum(1,.012/np.maximum(size,1e-12))[:,None]
            targets=['mpfb-heavy','build-full','build-breast-small-full','build-breast-large-full']
            for key in targets:
                if key not in m['targets']: continue
                values,ns=m['targets'][key];updated=values+detail
                m['targets'][key]=(updated,normals(updated,topology['triangles'],ns,np.linalg.norm(detail,axis=1)>0))
            rows.append(dict(body=name,vertices=int(np.count_nonzero(np.linalg.norm(detail,axis=1))),
                             maxDeltaMetres=float(np.max(np.linalg.norm(detail,axis=1))),targets=[k for k in targets if k in m['targets']]))
        write(path,sha,body,current)
        meta=path.with_suffix('.json');data=json.loads(meta.read_text(encoding='utf8'))
        data['muscleRelief']={'source':'pre-fairing 0.8.2 authored Heavy torso','limitMetres':.012,
                              'preserved':'all joints/limb transition fairing, Original, breast residuals and other shapes'}
        meta.write_text(json.dumps(data,indent=2)+'\n',encoding='utf8')
    output=Path(PROJECT)/'assets/metadata/muscle-relief-0120.json'
    output.write_text(json.dumps(dict(version='0.12.0',rows=rows),indent=2)+'\n',encoding='utf8')
    print('AVATAR2_MUSCLE_RELIEF_RECOVERED',json.dumps(rows),flush=True)

if __name__=='__main__': main()
