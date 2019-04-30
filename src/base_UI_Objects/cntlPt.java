package base_UI_Objects;

import base_Utils_Objects.*;

public class cntlPt extends myPoint {
	public static my_procApplet pa;
	public int ID;
	public static int IDincr = 0;
	public static final float maxR = 75, 
			minR = 1,
			baseRad = 20;			//default radius for control points
	public float r, w;				//weight is calculated based on the distance to neighboring cntl myPoints when cntl myPoints are drawn
	public static int[][] clr = new int[][]{{0,0,255,255}, {111,111,111,255}};
	
	public cntlPt(my_procApplet my_procApplet, myPoint _p, float _r, float _w){ super(_p.x,_p.y, _p.z); pa=my_procApplet; ID=IDincr++;r=_r; w=_w; }
	public cntlPt(my_procApplet _pa, myPoint _p, float _w){this(_pa, _p, baseRad, _w);}
	public cntlPt(my_procApplet _pa, myPoint _p){this(_pa, _p, baseRad, baseRad);}
	public cntlPt(my_procApplet _pa){this(_pa, _pa.P(),1);}
	public cntlPt(cntlPt _p){this(cntlPt.pa, cntlPt.pa.P(_p),_p.w); r = _p.r; w = _p.w;ID = _p.ID;}		
	public static cntlPt L(cntlPt A, float s, cntlPt B){	return new cntlPt(cntlPt.pa, cntlPt.pa.L((myPoint)A, s, (myPoint)B), capInterpR(A.r, s, B.r), (1-s)*A.w + (s)*B.w);}//(1-s)*A.r + (s)*B.r,
	public static cntlPt P(cntlPt A, cntlPt B){	float s = .5f;return L(A, s, B);}
	public myPoint set(myPoint P){super.set(P); return (myPoint)this;}
	private static float capInterpR(float a, float s, float b){ float res = (1-s)*a + (s)*b; res = (res < minR ? minR : res > maxR ? maxR : res); return res;}
	public void drawMe(int cIdx, boolean flat){	pa.fill(clr[cIdx][0],clr[cIdx][1],clr[cIdx][2],clr[cIdx][3]);  pa.stroke(clr[cIdx][0],clr[cIdx][1],clr[cIdx][2],clr[cIdx][3]);		pa.show(this,2,-1,-1, flat);}		
	public void drawRad(int cIdx,myVector I, myVector J){
        pa.fill(clr[cIdx][0],clr[cIdx][1],clr[cIdx][2],clr[cIdx][3]);  
        pa.stroke(clr[cIdx][0],clr[cIdx][1],clr[cIdx][2],clr[cIdx][3]); 
        pa.circle(this, r, I,J,20);
    }
	public void drawRad(myVector I, myVector J){
        pa.circle(this, r, I,J,20);
    }
	public void drawBall(int cIdx,myVector I, myVector J) {
	    float rhalf = this.r*0.5f;
	    myPoint center1 = pa.P(this);center1._add(pa.V(I)._mult(rhalf));
	    myPoint center2 = pa.P(this);center2._add(pa.V(I)._mult(-rhalf));
	    pa.fill(clr[cIdx][0],clr[cIdx][1],clr[cIdx][2],clr[cIdx][3]);  
	    pa.stroke(0,0,0,255); 
        pa.circle(center1, rhalf, I,J,20);
        pa.circle(center2, rhalf, I,J,20);
        pa.show(center1,1);
        pa.show(center2,1);
    }
	public void drawNorm(int cIdx,myVector I, myVector J) {
	    myPoint p1 = pa.P(this);p1._add(pa.V(I)._mult(r));
        myPoint p2 = pa.P(this);p2._add(pa.V(I)._mult(-r));
        pa.stroke(0,0,0,255);
        pa.line(p1, p2); 
	}
	public void calcRadFromWeight(float lenRatio, boolean inv){r = Math.min(maxR, Math.max(minR, baseRad * (inv ?  (lenRatio/w) : (pa.wScale*w/(lenRatio*lenRatio)))));  }
	public void modRad(float modAmt){float oldR = r; r += modAmt; r = (r < minR ? minR : r > maxR ? maxR : r); w *= oldR/r; }
	public String toString(){String res = "Cntl Pt ID:"+ID+" p:"+super.toString()+" r:"+r+" w:"+w;return res;}
}//class cntlPoint