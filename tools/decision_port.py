"""Python port of ObjectTracker + CollisionPredictor + DecisionEngine (same constants) for offline replay."""
import pickle, math, numpy as np
W=180
class Tr:
    n=0
    def __init__(s,kind,rL,rR,yT,yB,conf,t):
        Tr.n+=1; s.id=Tr.n; s.kind=kind; s.rL=rL;s.rR=rR;s.yT=yT;s.yB=yB;s.conf=conf;s.t=t;s.vy=0;s.hits=1;s.miss=0
    @property
    def rC(s): return (s.rL+s.rR)/2
    @property
    def eff(s): return s.conf*min(1,s.hits/3)
def to_road(L,R,x,row):
    for d in range(0,len(L)):
        for r in (row-d,row+d):
            if 0<=r<len(L) and L[r]>=0 and R[r]-L[r]>=8: return (x-L[r])/(R[r]-L[r])
    return .5
def detections(fr):
    H=fr['H']; out=[]
    for b in fr['blobs']+fr['en']:
        row=min(H-1,b['y']+b['h']-1)
        rl=to_road(fr['L'],fr['R'],b['x'],row); rr=to_road(fr['L'],fr['R'],b['x']+b['w'],row)
        size=min(1,b['a']/45)
        conf=(0.35+0.45*size+0.2*min(1,(b['a']/(b['w']*b['h']))*1.5)) if b['kind']=='obstacle' else 0.7
        out.append((b['kind'],rl,rr,b['y']/H,(b['y']+b['h'])/H,conf))
    return out
def track(tracks,dets,t):
    used=set()
    for k,rl,rr,yt,yb,cf in dets:
        dc=(rl+rr)/2; best=None;bc=1e9
        for tr in tracks:
            if tr.kind!=k or tr.id in used: continue
            dt=max(1e-3,t-tr.t); dy=yb-tr.yB
            if abs(dc-tr.rC)>.25 or dy<-.05 or dy>.05+.8*dt: continue
            c=abs(dc-tr.rC)*3+abs(dy)
            if c<bc: bc=c;best=tr
        if best:
            dt=t-best.t
            if dt>=.015: best.vy=.6*best.vy+.4*(yb-best.yB)/dt
            best.rL=.5*best.rL+.5*rl;best.rR=.5*best.rR+.5*rr;best.yT=yt;best.yB=yb;best.conf=.5*best.conf+.5*cf;best.t=t;best.hits+=1;best.miss=0;used.add(best.id)
        else:
            n=Tr(k,rl,rr,yt,yb,cf,t); tracks.append(n); used.add(n.id)
    for tr in list(tracks):
        if tr.id not in used: tr.miss+=1
        if tr.miss>4 or t-tr.t>.35: tracks.remove(tr)
def moving(tr,hor=.19,a=1.2):
    if tr.hits<6: return True
    u=max(.02,tr.yB-hor)
    return tr.vy>=.35*a*u*u
def ttc(tr,carFront,hor=.19,a=1.2):
    cb=carFront+.09
    if tr.yT>cb: return -1
    if tr.yB>=carFront: return 0
    u=max(.02,tr.yB-hor); uc=max(.05,carFront-hor)
    return (1/u-1/uc)/a
def decide(t,carR,tracks,carFront,state,cfg):
    n=cfg['n']; hw=cfg['hw']; m=.03; lat=.6
    margin=min(.45,hw+.03); pos=[margin+i*(1-2*margin)/(n-1) for i in range(n)]
    kc=min(range(n),key=lambda k:abs(pos[k]-carR))
    risk=[0]*n; tran=[0]*n; gain=[0]*n; overall=0
    for tr in tracks:
        T=ttc(tr,carFront)
        if T<0 or not moving(tr): continue
        if tr.kind=='obstacle':
            ov=(carR+hw>=tr.rL-m) and (carR-hw<=tr.rR+m)
            if ov: overall=max(overall,4 if T<.5 else 3 if T<.9 else 2 if T<1.5 else 1)
            w=(1 if T<=0 else max(0,1-T/2.2)**.7)*tr.eff
            if w<=.02: continue
            a=tr.rL-m;b=tr.rR+m
            for k in range(n):
                if pos[k]+hw>=a and pos[k]-hw<=b: risk[k]=1-(1-risk[k])*(1-w)
                if k!=kc:
                    lo=min(carR,pos[k])-hw;hi=max(carR,pos[k])+hw;tt=abs(pos[k]-carR)/lat+.15
                    if hi>=a and lo<=b and T<tt+.1: tran[k]=max(tran[k],.8*tr.eff)
        elif .25<=T<=2:
            g=tr.eff*math.exp(-T/1.3); half=hw+.5*(tr.rR-tr.rL)
            for k in range(n):
                if abs(pos[k]-tr.rC)<=half and (k==kc or abs(pos[k]-carR)/lat<T-.1): gain[k]=min(1,gain[k]+g)
    eff=[risk[k] if k==kc else max(risk[k],tran[k]) for k in range(n)]
    sc=[-.8*8*eff[k]+.5*2*1*gain[k]*(1-eff[k])-.25*abs(k-kc)+(.35 if k==kc else 0)-.15*abs(pos[k]-.5) for k in range(n)]
    best=max(range(n),key=lambda k:sc[k]); imm=eff[kc]>=.6
    tgt=kc
    if best!=kc:
        d=1 if best>kc else -1; since=t-state['lm']; need=.5
        if state['ld']!=0 and d!=state['ld'] and since<.8: need+=1
        if imm and eff[kc]>=.85: need=0
        if (since>=.35 or imm) and (sc[best]-sc[kc]>=need or (imm and eff[best]<eff[kc]-.25)): tgt=best
    return tgt,kc,imm,overall,eff
if __name__=='__main__':
    o=pickle.load(open('mirror2.pkl','rb')); fps=30
    for hw,n in ((0.12,5),(0.12,3)):
        Tr.n=0; tracks=[]; st=dict(lm=-9,ld=0); moves=[]; imm_frames=0; crit=0; rev=0
        lastcool=-9
        for i,fr in enumerate(o):
            t=i/fps; pl=fr['pl']
            if not pl: continue
            carFront=pl['top']/fr['H']
            carR=to_road(fr['L'],fr['R'],pl['cx'],min(fr['H']-1,int(.62*fr['H'])))
            track(tracks,detections(fr),t)
            tgt,kc,imm,ov,eff=decide(t,carR,tracks,carFront,st,dict(n=n,hw=hw))
            imm_frames+=imm; crit+=(ov>=3)
            if tgt!=kc and t-lastcool>=.35:
                d=1 if tgt>kc else -1
                if st['ld'] and d!=st['ld'] and t-st['lm']<.8: rev+=1
                moves.append((t,d)); st['lm']=t; st['ld']=d; lastcool=t
        dur=len(o)/fps
        print(f'lanes={n}: moves={len(moves)} ({len(moves)/dur:.2f}/s), fast reversals(<0.8s)={rev}, frames with imminent risk={imm_frames} ({100*imm_frames/len(o):.0f}%), frames with HIGH/CRITICAL overlap={crit}')
        gaps=np.diff([m[0] for m in moves]); print('   min gap between moves %.2fs  median %.2fs'%(gaps.min(),np.median(gaps)))
