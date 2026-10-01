"""Exact mirror of the Kotlin pipeline parameters (W=180 processing width). Used to validate before porting."""
import cv2, numpy as np, pickle, sys
W=180; REG_T=0.05; REG_B=0.78
def road_pix(h,s,v): return (h>=100)&(h<=135)&(s>=110)&(v>=18)&(v<=115)
def prep(f):
    H0,W0=f.shape[:2]; sm=cv2.resize(f,(W,int(round(H0*W/W0))),interpolation=cv2.INTER_AREA)
    return sm,cv2.cvtColor(sm,cv2.COLOR_BGR2HSV)
def player(hsv):
    H=hsv.shape[0]; h,s,v=cv2.split(hsv)
    y1,y2=int(.62*H),int(.76*H)
    col=((v>=225)&(s<=120))[y1:y2].sum(0).astype(np.float32)
    col=np.convolve(col,np.ones(7)/7,mode='same')
    if col.max()<6: return None
    px=int(col.argmax()); lo,hi=max(0,px-14),min(W,px+15)
    cx=float((col[lo:hi]*np.arange(lo,hi)).sum()/col[lo:hi].sum())
    red=(((h<=8)|(h>=172))&(s>=120)&(v>=90))
    x0,x1=int(max(0,cx-15)),int(min(W,cx+16)); top=y1; y=y1-1; gap=0
    while y>=int(.45*H) and gap<=3:
        if red[y,x0:x1].sum()>=3: top=y; gap=0
        else: gap+=1
        y-=1
    return dict(cx=cx,top=top,conf=min(1,col.max()/25))
def lanes(hsv,seed):
    H=hsv.shape[0]; h,s,v=cv2.split(hsv); rm=road_pix(h,s,v)
    y0=int(REG_T*H); y1=int(REG_B*H)
    L=[-1]*H; R=[-1]*H; cl,cr=seed,seed; miss=0
    for y in range(y1-1,y0-1,-1):
        frac=(y-y0)/max(1,(y1-y0)); gap=int(W*(0.05+0.30*frac))
        xs=np.nonzero(rm[y])[0]; runs=[]
        if len(xs):
            s0=xs[0]; last=xs[0]
            for x in xs[1:]:
                if x-last>gap: runs.append((s0,last)); s0=x
                last=x
            runs.append((s0,last))
        best=None;bo=-10**9
        for a,b in runs:
            if b-a>=6:
                ov=min(b,cr)-max(a,cl)
                if ov>bo: bo=ov;best=(a,b)
        if best is not None and bo>=-int(W*.10):
            L[y],R[y]=int(best[0]),int(best[1]); cl,cr=L[y],R[y]; miss=0
        else:
            miss+=1
            if miss>6: break
    # widen extents over +-4 rows so glow/objects that split the road do not shrink it
    L2=list(L);R2=list(R)
    for y in range(y0,y1):
        ls=[L[k] for k in range(max(y0,y-4),min(y1,y+5)) if L[k]>=0]
        rs=[R[k] for k in range(max(y0,y-4),min(y1,y+5)) if R[k]>=0]
        if ls and L[y]>=0: L2[y]=min(ls); R2[y]=max(rs)
    return L2,R2,rm,y0,y1
def blobs(hsv,L,R,rm,y0,y1,pl,minArea=12):
    H=hsv.shape[0]; h,s,v=cv2.split(hsv)
    m=np.zeros((H,W),np.uint8)
    white=(s<60)&(v>170)
    for y in range(y0,y1):
        if L[y]>=0 and R[y]-L[y]>=8:
            seg=slice(L[y],R[y]+1); m[y,seg]=((~rm[y,seg])&(~white[y,seg])).astype(np.uint8)
    k=np.ones((3,3),np.uint8); m=cv2.erode(cv2.dilate(m,k),k)
    n,lab,st,_=cv2.connectedComponentsWithStats(m,connectivity=8)
    out=[]
    for i in range(1,n):
        x,y,w,hh,a=st[i]
        if a<minArea: continue
        if pl and y+hh>pl['top']+4 and x<pl['cx']+16 and x+w>pl['cx']-16: continue
        row=min(H-1,y+hh-1)
        if L[row]>=0 and w<=8 and (x<=L[row]+3 or x+w>=R[row]-3): continue
        mk=lab[y:y+hh,x:x+w]==i
        hs=h[y:y+hh,x:x+w][mk];ss=s[y:y+hh,x:x+w][mk];vs=v[y:y+hh,x:x+w][mk]
        dark=(vs<70).mean(); yel=(((hs>=18)&(hs<=40))&(ss>=90)&(vs>=150)).mean(); red=(((hs<=8)|(hs>=172))&(ss>=130)&(vs>=110)).mean()
        sol=a/(w*hh)
        if not (sol>=0.30 or (sol>=0.15 and yel>=0.12)): continue
        cy=(y+hh/2)/H; small=w<=(6+cy*14); asp=hh/w
        kind='energy' if (small and asp>=1.15 and dark<0.15 and (yel>0.45 or red>0.35)) else 'obstacle'
        out.append(dict(x=x,y=y,w=w,h=hh,a=int(a),kind=kind))
    return out
def bar(hsv):
    H=hsv.shape[0]; res=''
    for i in range(7):
        cx=(0.254+(i+.5)*(0.746-0.254)/7)*W; cy=0.124*H
        x,y=int(cx),int(cy); p=hsv[y-1:y+2,x-2:x+3].reshape(-1,3).mean(0)
        res+= 'L' if (p[1]>=90 and p[2]>=120) else ('E' if (p[1]<=45 and 35<=p[2]<=150) else '?')
    return res
def run(frames):
    out=[];seed=W//2
    for f in frames:
        sm,hsv=prep(f); pl=player(hsv)
        if pl: seed=int(pl['cx'])
        L,R,rm,y0,y1=lanes(hsv,seed)
        bl=blobs(hsv,L,R,rm,y0,y1,pl)
        out.append(dict(pl=pl,L=L,R=R,blobs=bl,bar=bar(hsv),H=sm.shape[0],sm=sm))
    return out

def collectibles(hsv, rm, pl, y0, y1, minArea=6):
    """Colour-key detector for energy pickups (yellow capsule / red can) verified by a navy-road ring."""
    H=hsv.shape[0]; h,s,v=cv2.split(hsv)
    yel=((h>=20)&(h<=38)&(s>=120)&(v>=200))
    red=(((h<=6)|(h>=174))&(s>=150)&(v>=140))
    m=(yel|red).astype(np.uint8); m[:y0]=0; m[y1:]=0
    m=cv2.morphologyEx(m,cv2.MORPH_CLOSE,np.ones((3,3),np.uint8))
    n,lab,st,_=cv2.connectedComponentsWithStats(m,connectivity=8)
    out=[]
    for i in range(1,n):
        x,y,w,hh,a=st[i]
        if a<minArea: continue
        cy=(y+hh/2)/H
        if w>6+cy*14 or hh/w<1.1 or a/(w*hh)<0.5: continue
        if pl and y+hh>pl['top']+4 and x<pl['cx']+16 and x+w>pl['cx']-16: continue
        ya,yb=max(0,y-1),min(H,y+hh+1)
        def side(xa,xb):
            xa,xb=max(0,xa),min(W,xb)
            return float(rm[ya:yb,xa:xb].mean()) if xb>xa else 0.0
        lf=side(x-5,x-2); rt=side(x+w+2,x+w+5)
        tp=float(rm[max(0,y-5):max(0,y-2),x:x+w].mean()) if y-2>0 else 0.0
        if lf<0.25 or rt<0.25 or (lf+rt+tp)/3<0.35: continue
        frac=(lf+rt+tp)/3
        mk=lab[y:y+hh,x:x+w]==i
        out.append(dict(x=x,y=y,w=w,h=hh,a=int(a),kind='energy',ring=frac,col='yellow' if yel[y:y+hh,x:x+w][mk].mean()>0.5 else 'red'))
    return out
def run2(frames):
    out=[];seed=W//2
    for f in frames:
        sm,hsv=prep(f); pl=player(hsv)
        if pl: seed=int(pl['cx'])
        L,R,rm,y0,y1=lanes(hsv,seed)
        en=collectibles(hsv,rm,pl,y0,y1)
        bl=[b for b in blobs(hsv,L,R,rm,y0,y1,pl) if not any(abs((b['x']+b['w']/2)-(e['x']+e['w']/2))<max(b['w'],e['w'])/2+2 and abs((b['y']+b['h']/2)-(e['y']+e['h']/2))<max(b['h'],e['h'])/2+2 for e in en)]
        for b in bl: b['kind']='obstacle'
        out.append(dict(pl=pl,L=L,R=R,blobs=bl,en=en,H=sm.shape[0],sm=sm))
    return out
