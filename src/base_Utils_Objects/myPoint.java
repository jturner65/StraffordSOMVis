package base_Utils_Objects;

public class myPoint {
	public double x,y,z;
	public static final myPoint ZEROPT = new myPoint(0,0,0);

	public myPoint(double _x, double _y, double _z){this.x = _x; this.y = _y; this.z = _z;}         //constructor 3 args  
	public myPoint(myPoint p){ this(p.x, p.y, p.z); }                                                                                                           	//constructor 1 arg  
	public myPoint(myPoint A, myVector B) {this(A.x+B.x,A.y+B.y,A.z+B.z); };
	
	public myPoint(myPoint A, double s, myPoint B) {this(A.x+s*(B.x-A.x),A.y+s*(B.y-A.y),A.z+s*(B.z-A.z)); };		//builds a point somewhere in between a and b
	public myPoint(){ this(0,0,0);}                                                                                                                               //constructor 0 args
	public void clear() {this.x = 0; this.y = 0; this.z = 0;}	
	public void set(double _x, double _y, double _z){ this.x = _x;  this.y = _y;  this.z = _z; }                                               //set 3 args 
	public myPoint set(myPoint p){ this.x = p.x; this.y = p.y; this.z = p.z; return this;}                                                                   //set 1 args
	public void set(double _x, double _y, double _z, double _sqMagn){ this.x = _x;  this.y = _y;  this.z = _z; }                                                                     //set 3 args 
	
	public myPoint _mult(double n){ this.x *= n; this.y *= n; this.z *= n; return this; }                                                     //_mult 3 args  
	public static myPoint _mult(myPoint p, double n){ myPoint result = new myPoint(p.x * n, p.y * n, p.z * n); return result;}                          //1 pt, 1 double
	public static myPoint _mult(myPoint p, myPoint q){ myPoint result = new myPoint(p.x *q.x, p.y * q.y, p.z * q.z); return result;}           //return elementwise product
	public static void _mult(myPoint p, myPoint q, myPoint r){ myPoint result = new myPoint(p.x *q.x, p.y * q.y, p.z * q.z); r.set(result);}           //2 pt src, 1 pt dest  

	public void _div(double q){this.x /= q; this.y /= q; this.z /= q; }  
	public static myPoint _div(myPoint p, double n){ if(n==0) return p; myPoint result = new myPoint(p.x / n, p.y / n, p.z / n); return result;}                          //1 pt, 1 double
	
	public void _add(double _x, double _y, double _z){ this.x += _x; this.y += _y; this.z += _z;   }                                            //_add 3 args
	public void _add(myPoint v){ this.x += v.x; this.y += v.y; this.z += v.z;   }                                                 //_add 1 arg  
	public static myPoint _add(myPoint O, myVector I){														return new myPoint(O.x+I.x,O.y+I.y,O.z+I.z);}  
	public static myPoint _add(myPoint O, double a, myVector I){												return new myPoint(O.x+a*I.x,O.y+a*I.y,O.z+a*I.z);}                						//2 vec
	public static myPoint _add(myPoint O, double a, myVector I, double b, myVector J) {						return new myPoint(O.x+a*I.x+b*J.x,O.y+a*I.y+b*J.y,O.z+a*I.z+b*J.z);}  					// O+xI+yJ
	public static myPoint _add(myPoint O, double a, myVector I, double b, myVector J, double c, myVector K) {	return new myPoint(O.x+a*I.x+b*J.x+c*K.x,O.y+b*I.y+b*J.y+c*K.y,O.z+b*I.z+b*J.z+c*K.z);} // O+xI+yJ+kZ
	
	public static void _add(myPoint p, myPoint q, myPoint r){ myPoint result = new myPoint(p.x + q.x, p.y + q.y, p.z + q.z); r.set(result);}       	//2 pt src, 1 pt dest  
	public static myPoint _add(myPoint p, myPoint q){ myPoint result = new myPoint(p.x + q.x, p.y + q.y, p.z + q.z); return result;}
	
	public void _sub(double _x, double _y, double _z){ this.x -= _x; this.y -= _y; this.z -= _z;  }                                                                   //_sub 3 args
	public void _sub(myPoint v){ this.x -= v.x; this.y -= v.y; this.z -= v.z;  }                                                                           //_sub 1 arg 
	public static myPoint _sub(myPoint p, myPoint q){ myPoint result = new myPoint(p.x - q.x, p.y - q.y, p.z - q.z); return result; }
	public static void _sub(myPoint p, myPoint q, myPoint r){ myPoint result = new myPoint(p.x - q.x, p.y - q.y, p.z - q.z); r.set(result);}       //2 pt src, 1 pt dest  	

	public myPoint cloneMe(){myPoint retVal = new myPoint(this.x, this.y, this.z); return retVal;}  
	
	public double _L1Dist(myPoint q){return Math.abs((this.x - q.x)) + Math.abs((this.y - q.y)) + Math.abs((this.z - q.z)); }
	public static double _L1Dist(myPoint q, myPoint r){ return q._L1Dist(r);}
	
	public double _SqrDist(myPoint q){ return (((this.x - q.x)*(this.x - q.x)) + ((this.y - q.y)*(this.y - q.y)) + ((this.z - q.z)*(this.z - q.z))); }
	public double _SqrDist(myPointf q){ return (((this.x - q.x)*(this.x - q.x)) + ((this.y - q.y)*(this.y - q.y)) + ((this.z - q.z)*(this.z - q.z))); }
	public static double _SqrDist(myPoint q, myPoint r){  return (((r.x - q.x) *(r.x - q.x)) + ((r.y - q.y) *(r.y - q.y)) + ((r.z - q.z) *(r.z - q.z)));}
	
	public double _dist(myPoint q){ return (double)Math.sqrt( ((this.x - q.x)*(this.x - q.x)) + ((this.y - q.y)*(this.y - q.y)) + ((this.z - q.z)*(this.z - q.z)) ); }
	public static double _dist(myPoint q, myPoint r){  return (double)Math.sqrt(((r.x - q.x) *(r.x - q.x)) + ((r.y - q.y) *(r.y - q.y)) + ((r.z - q.z) *(r.z - q.z)));}
	
	public double _dist(double qx, double qy, double qz){ return (double)Math.sqrt( ((this.x - qx)*(this.x - qx)) + ((this.y - qy)*(this.y - qy)) + ((this.z - qz)*(this.z - qz)) ); }
	public static double _dist(myPoint r, double qx, double qy, double qz){  return(double) Math.sqrt(((r.x - qx) *(r.x - qx)) + ((r.y - qy) *(r.y - qy)) + ((r.z - qz) *(r.z - qz)));}	
	
	public double[] asArray(){return new double[]{x,y,z};}
	
	public float[] asFltArray(){return new float[]{(float)x,(float)y,(float)z};}
	
	public boolean clickIn(myPoint p, double eps) { return(_dist(p) < eps);}
	/**
	 * returns if this pttor is equal to passed pttor
	 * @param b myPoint to check
	 * @return whether they are equal
	 */
	public boolean equals(Object b){
		if (this == b) return true;
		if (!(b instanceof myPoint)) return false;
		myPoint v = (myPoint)b;
		return ((this.x == v.x) && (this.y == v.y) && (this.z == v.z));		
	}				
	public String toStrCSV(){return toStrCSV("%.4f");}	
	public String toStrCSV(String fmt){return "" + String.format(fmt,this.x) + ", " + String.format(fmt,this.y) + ", " + String.format(fmt,this.z);}	
	public String toStrBrf(){return "|(" + String.format("%.4f",this.x) + ", " + String.format("%.4f",this.y) + ", " + String.format("%.4f",this.z)+")";}	
	public String toString(){return "|(" + String.format("%.4f",this.x) + ", " + String.format("%.4f",this.y) + ", " + String.format("%.4f",this.z)+")";}
}
