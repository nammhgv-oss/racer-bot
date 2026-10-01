"""Prototype of the classical-CV pipeline (mirrors the Kotlin implementation). Runs on the reference video."""
import cv2, numpy as np, sys, json
W=180
def load(path):
    cap=cv2.VideoCapture(path); fr=[]
    while True:
        ok,f=cap.read()
        if not ok:break
        fr.append(f)
    return fr
def road_mask(hsv):
    h,s,v=cv2.split(hsv)
    return ((h>=100)&(h<=135)&(s>=110)&(v>=18)&(v<=115)).astype(np.uint8)
def analyse(f, top=0.05):
    H0,W0=f.shape[:2]; sc=W/W0; sm=cv2.resize(f,(W,int(H0*sc)),interpolation=cv2.INTER_AREA)
    H=sm.shape[0]; hsv=cv2.cvtColor(sm,cv2.COLOR_BGR2HSV); h,s,v=cv2.split(hsv)
    rm=road_mask(hsv); y0=int(top*H)
    rm[:y0]=0
    # per-row road extent using percentiles of navy pixels (robust to occlusion)
    ext=[None]*H
    for y in range(y0,H):
        xs=np.nonzero(rm[y])[0]
        if len(xs)>=6: ext[y]=(int(np.percentile(xs,2)),int(np.percentile(xs,98)))
    # horizon: first row (from top) w/ extent for 3 consecutive rows
    hor=None
    for y in range(y0,H-3):
        if all(ext[y+k] for k in range(3)): hor=y;break
    # inside-road mask
    inside=np.zeros((H,W),np.uint8)
    for y in range(H):
        if ext[y]: inside[y,ext[y][0]:ext[y][1]+1]=1
    white=(s<60)&(v>170)
    fg=(inside==1)&(rm==0)&(~white)
    fg=fg.astype(np.uint8)
    fg=cv2.morphologyEx(fg,cv2.MORPH_OPEN,np.ones((2,2),np.uint8))
    fg=cv2.morphologyEx(fg,cv2.MORPH_CLOSE,np.ones((3,3),np.uint8))
    return sm,hsv,rm,inside,fg,hor,ext
def blobs(fg,hsv,carTop,carL,carR,minArea=6):
    n,lab,stats,cen=cv2.connectedComponentsWithStats(fg,connectivity=8)
    h,s,v=cv2.split(hsv); out=[]
    for i in range(1,n):
        x,y,w,hh,a=stats[i]
        if a<minArea: continue
        if y+hh>carTop+4 and x<carR and x+w>carL: continue   # player/trail area
        m=(lab[y:y+hh,x:x+w]==i)
        hs=h[y:y+hh,x:x+w][m]; ss=s[y:y+hh,x:x+w][m]; vs=v[y:y+hh,x:x+w][m]
        dark=(vs<70).mean(); yel=(((hs>=18)&(hs<=40))&(ss>=90)&(vs>=150)).mean()
        red=(((hs<=8)|(hs>=172))&(ss>=130)&(vs>=110)).mean()
        out.append(dict(x=x,y=y,w=w,h=hh,a=int(a),dark=float(dark),yel=float(yel),red=float(red),
                        cx=float(cen[i][0]),cy=float(cen[i][1])))
    return out
def classify(b,H):
    asp=b['h']/max(1,b['w']); fill=b['a']/max(1,b['w']*b['h'])
    small = b['w']<=(6+ (b['cy']/H)*14)      # perspective-aware size gate
    if small and asp>=1.15 and b['dark']<0.15 and (b['yel']>0.45 or b['red']>0.35): return 'energy'
    return 'obstacle'
if __name__=='__main__':
    fr=load('/mnt/user-data/uploads/vid_g.mp4')
    print(len(fr))

def detect_player(hsv, H, W):
    """Exhaust-plume based: bright, low-saturation/yellow-white pixels right below the car body."""
    h,s,v=cv2.split(hsv)
    y1,y2=int(0.62*H),int(0.76*H)
    band=((v>=225)&(s<=120)).astype(np.float32)[y1:y2]
    col=band.sum(0); col=cv2.GaussianBlur(col.reshape(1,-1),(0,0),3).ravel()
    if col.max()<6: return None
    # widest weighted centroid around the peak
    px=int(col.argmax()); lo,hi=max(0,px-14),min(W,px+15)
    xs=np.arange(lo,hi); cx=float((col[lo:hi]*xs).sum()/max(1e-6,col[lo:hi].sum()))
    # body: red pixels above the plume within +-14 px
    red=(((h<=8)|(h>=172))&(s>=120)&(v>=90)).astype(np.uint8)
    x0,x1=int(max(0,cx-15)),int(min(W,cx+16))
    sub=red[int(0.50*H):y2,x0:x1]
    ys=np.nonzero(sub.sum(1)>=3)[0]
    top=int(0.50*H)+int(ys.min()) if len(ys) else int(0.56*H)
    return dict(cx=cx,top=top,bottom=y1,conf=float(min(1,col.max()/25)))
def energy_bar(hsv):
    H,W=hsv.shape[:2]
    cxs=[.289,.358,.429,.501,.575,.642,.711]; cy=.124
    res=[]
    for cx in cxs:
        x=int(cx*W); y=int(cy*H)
        p=hsv[max(0,y-1):y+2,max(0,x-2):x+3].reshape(-1,3).mean(0)
        if p[1]>=90 and p[2]>=120: res.append('L')
        elif p[1]<=45 and 35<=p[2]<=150: res.append('E')
        else: res.append('?')
    return ''.join(res)
def draw(f,name):
    sm,hsv,rm,inside,fg,hor,ext=analyse(f); H,W=sm.shape[:2]
    pl=detect_player(hsv,H,W)
    carTop=pl['top'] if pl else int(0.56*H); carL=int(pl['cx']-16) if pl else W//2-16; carR=int(pl['cx']+16) if pl else W//2+16
    bl=blobs(fg,hsv,carTop,carL,carR)
    vis=sm.copy()
    if pl: cv2.rectangle(vis,(carL,carTop),(carR,int(0.72*H)),(255,120,0),1)
    for b in bl:
        c=(0,255,255) if classify(b,H)=='energy' else (0,0,255)
        cv2.rectangle(vis,(b['x'],b['y']),(b['x']+b['w'],b['y']+b['h']),c,1)
    cv2.putText(vis,energy_bar(hsv),(2,H-4),cv2.FONT_HERSHEY_SIMPLEX,0.3,(0,255,0),1)
    return cv2.resize(vis,None,fx=1.5,fy=1.5,interpolation=cv2.INTER_NEAREST)

def road_runs(rm_row, gap):
    xs=np.nonzero(rm_row)[0]
    if len(xs)==0: return []
    runs=[]; s=xs[0]; p=xs[0]
    for x in xs[1:]:
        if x-p>gap: runs.append((s,p)); s=x
        p=x
    runs.append((s,p))
    return [r for r in runs if r[1]-r[0]>=6]
def road_extent(rm,H,W,y0,seed_x):
    """Row-wise drivable extent following the run that overlaps the row below (handles curves/forks)."""
    ext=[None]*H; cur=(seed_x,seed_x); hor=None
    for y in range(H-1,y0-1,-1):
        frac=(y-y0)/max(1,(H-y0))
        gap=int(W*(0.05+0.30*frac))
        runs=road_runs(rm[y],gap)
        best=None;bo=-1
        for r in runs:
            ov=min(r[1],cur[1])-max(r[0],cur[0])
            if ov>bo: bo=ov;best=r
        if best is not None and (bo>=0 or ext[y+1] is None if y<H-1 else True):
            ext[y]=(int(best[0]),int(best[1])); cur=ext[y]
        else:
            ext[y]=None if bo<0 else ext[y]
            if best is None: 
                if y<H-1 and ext[y+1] is None: pass
                # no road: stop growing upward after 3 misses handled by hor
    for y in range(y0,H):
        if ext[y]: hor=y;break
    return ext,hor
def analyse2(f, top=0.05, seed=None):
    H0,W0=f.shape[:2]; sc=W/W0; sm=cv2.resize(f,(W,int(H0*sc)),interpolation=cv2.INTER_AREA)
    H=sm.shape[0]; hsv=cv2.cvtColor(sm,cv2.COLOR_BGR2HSV); h,s,v=cv2.split(hsv)
    rm=road_mask(hsv); y0=int(top*H); rm[:y0]=0
    ext,hor=road_extent(rm,H,W,y0,seed if seed is not None else W//2)
    inside=np.zeros((H,W),np.uint8)
    for y in range(H):
        if ext[y]: inside[y,ext[y][0]:ext[y][1]+1]=1
    white=(s<60)&(v>170)
    fg=((inside==1)&(rm==0)&(~white)).astype(np.uint8)
    fg=cv2.morphologyEx(fg,cv2.MORPH_OPEN,np.ones((2,2),np.uint8))
    fg=cv2.morphologyEx(fg,cv2.MORPH_CLOSE,np.ones((3,3),np.uint8))
    return sm,hsv,rm,inside,fg,hor,ext
def edge_blob(b,ext):
    """Rail fragments: thin blobs hugging the road boundary."""
    y=min(len(ext)-1,b['y']+b['h']//2); e=ext[y]
    if not e: return False
    return b['w']<=8 and (b['x']<=e[0]+3 or b['x']+b['w']>=e[1]-3)

def pipeline(f, prev_seed=None):
    H0,W0=f.shape[:2]
    sm0=cv2.resize(f,(W,int(H0*W/W0)),interpolation=cv2.INTER_AREA); H=sm0.shape[0]
    hsv0=cv2.cvtColor(sm0,cv2.COLOR_BGR2HSV)
    pl=detect_player(hsv0,H,W)
    seed=int(pl['cx']) if pl else (prev_seed or W//2)
    sm,hsv,rm,inside,fg,hor,ext=analyse2(f,seed=seed)
    carTop=pl['top'] if pl else int(0.56*H); cx=pl['cx'] if pl else seed
    bl=[]
    for b in blobs(fg,hsv,carTop,int(cx-16),int(cx+16),minArea=12):
        if edge_blob(b,ext) or b['a']/max(1,b['w']*b['h'])<0.30: continue
        b['cls']=classify(b,H); bl.append(b)
    return dict(pl=pl,blobs=bl,H=H,hor=hor,ext=ext,seed=seed,hsv=hsv)
def track(frames_out):
    """Greedy nearest-neighbour tracker; returns list of (bottom-y velocity px/s) samples with y."""
    tracks=[];samples=[];nid=0
    for fi,res in enumerate(frames_out):
        H=res['H']; used=set()
        for t in tracks: t['upd']=False
        for b in res['blobs']:
            if b['cls']!='obstacle' or b['w']<10: continue
            best=None;bd=1e9
            for t in tracks:
                if t['upd']:continue
                d=abs(t['cx']-b['cx'])+abs(t['cy']-b['cy'])*0.6
                if d<bd and abs(t['cx']-b['cx'])<14 and 0<=b['cy']-t['cy']<24: bd=d;best=t
            if best:
                dt=fi-best['fi']; 
                best['hist'].append((fi,b['y']+b['h'])); best.update(cx=b['cx'],cy=b['cy'],fi=fi,upd=True)
                if len(best['hist'])>=6:
                    (f0,y0),(f1,y1)=best['hist'][-6],best['hist'][-1]
                    samples.append(((y1)/H,(y1-y0)/((f1-f0)/30.0)/H))
            else:
                tracks.append(dict(cx=b['cx'],cy=b['cy'],fi=fi,upd=True,hist=[(fi,b['y']+b['h'])]))
        tracks=[t for t in tracks if fi-t['fi']<6]
    return samples
