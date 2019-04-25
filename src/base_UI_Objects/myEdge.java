package base_UI_Objects;

import base_Utils_Objects.*;

public class myEdge {
	public my_procApplet p;
	public myPoint a, b;

	public myEdge(my_procApplet p2){this(p2,new myPoint(0,0,0),new myPoint(0,0,0));}
	public myEdge(my_procApplet _p, myPoint _a, myPoint _b){p = _p;a=new myPoint(_a); b=new myPoint(_b);}
	public void set(float d, myVector dir, myPoint _p){	set( myPoint._add(_p,-d,new myVector(dir)), myPoint._add(_p,d,new myVector(dir)));} 
	public void set(myPoint _a, myPoint _b){a=new myPoint(_a); b=new myPoint(_b);}
	public myVector v(){return new myVector(b.x-a.x, b.y-a.y, b.z-a.z);}			//vector from a to b
	public myVector dir(){return v()._normalize();}
	public double len(){return  myPoint._dist(a,b);}
	public double distFromPt(myPoint P) {return myVector._det3(dir(),new myVector(a,P)); };
	public void drawMe(){p.line(a.x,a.y,a.z,b.x,b.y,b.z); }
	public String toString(){return "a:"+a+" to b:"+b+" len:"+len();}

}//myEdge
