package base_Utils_Objects;

public class myPointf {
	public float x,y,z;
	public static final myPointf ZEROPT = new myPointf(0,0,0);

	public myPointf(float _x, float _y, float _z){this.x = _x; this.y = _y; this.z = _z;}         //constructor 3 args  
	public myPointf(double _x, double _y, double _z){this((float)_x, (float)_y,(float)_z);}         //constructor 3 args  
	public myPointf(myPointf p){ this(p.x, p.y, p.z); }                                                                                                           	//constructor 1 arg  
	public myPointf(myPointf A, myVectorf B) {this(A.x+B.x,A.y+B.y,A.z+B.z); };
	 
	public myPointf(myPointf A, float s, myPointf B) {this(A.x+s*(B.x-A.x),A.y+s*(B.y-A.y),A.z+s*(B.z-A.z)); };		//builds a point somewhere in between a and b
	public myPointf(){ this(0,0,0);}                                                                                                                               //constructor 0 args
	public void clear() {this.x = 0; this.y = 0; this.z = 0;}
	public void set(float _x, float _y, float _z){ this.x = _x;  this.y = _y;  this.z = _z; }                                               //set 3 args 
	public void set(double _x, double _y, double _z){ this.set((float)_x,(float)_y,(float)_z); }                                               //set 3 args 
	public myPointf set(myPointf p){ this.x = p.x; this.y = p.y; this.z = p.z; return this;}                                                                   //set 1 args
	public void set(float _x, float _y, float _z, float _sqMagn){ this.x = _x;  this.y = _y;  this.z = _z; }                                                                     //set 3 args 
	
	public myPointf _mult(float n){ this.x *= n; this.y *= n; this.z *= n; return this; }                                                     //_mult 3 args  
	public static myPointf _mult(myPointf p, float n){ myPointf result = new myPointf(p.x * n, p.y * n, p.z * n); return result;}                          //1 pt, 1 float
	public static myPointf _mult(myPointf p, myPointf q){ myPointf result = new myPointf(p.x *q.x, p.y * q.y, p.z * q.z); return result;}           //return elementwise product
	public static void _mult(myPointf p, myPointf q, myPointf r){ myPointf result = new myPointf(p.x *q.x, p.y * q.y, p.z * q.z); r.set(result);}           //2 pt src, 1 pt dest  

	public void _div(float q){this.x /= q; this.y /= q; this.z /= q; }  
	public static myPointf _div(myPointf p, float n){ if(n==0) return p; myPointf result = new myPointf(p.x / n, p.y / n, p.z / n); return result;}                          //1 pt, 1 float
	
	public void _add(float _x, float _y, float _z){ this.x += _x; this.y += _y; this.z += _z;   }                                            //_add 3 args
	public void _add(myPointf v){ this.x += v.x; this.y += v.y; this.z += v.z;   }                                                 //_add 1 arg  
	public static myPointf _add(myPointf O, myVectorf I){														return new myPointf(O.x+I.x,O.y+I.y,O.z+I.z);}  
	public static myPointf _add(myPointf O, float a, myVectorf I){												return new myPointf(O.x+a*I.x,O.y+a*I.y,O.z+a*I.z);}                						//2 vec
	public static myPointf _add(myPointf O, float a, myVectorf I, float b, myVectorf J) {						return new myPointf(O.x+a*I.x+b*J.x,O.y+a*I.y+b*J.y,O.z+a*I.z+b*J.z);}  					// O+xI+yJ
	public static myPointf _add(myPointf O, float a, myVectorf I, float b, myVectorf J, float c, myVectorf K) {	return new myPointf(O.x+a*I.x+b*J.x+c*K.x,O.y+b*I.y+b*J.y+c*K.y,O.z+b*I.z+b*J.z+c*K.z);} // O+xI+yJ+kZ
	
	public static void _add(myPointf p, myPointf q, myPointf r){ myPointf result = new myPointf(p.x + q.x, p.y + q.y, p.z + q.z); r.set(result);}       	//2 pt src, 1 pt dest  
	public static myPointf _add(myPoint p, myPointf q){ myPointf result = new myPointf(p.x + q.x, p.y + q.y, p.z + q.z); return result;}
	public static myPointf _add(myPointf p, myPointf q){ myPointf result = new myPointf(p.x + q.x, p.y + q.y, p.z + q.z); return result;}
	
	public void _sub(float _x, float _y, float _z){ this.x -= _x; this.y -= _y; this.z -= _z;  }                                                                   //_sub 3 args
	public void _sub(myPointf v){ this.x -= v.x; this.y -= v.y; this.z -= v.z;  }                                                                           //_sub 1 arg 
	public static myPointf _sub(myPointf p, myPointf q){ myPointf result = new myPointf(p.x - q.x, p.y - q.y, p.z - q.z); return result; }
	public static void _sub(myPointf p, myPointf q, myPointf r){ myPointf result = new myPointf(p.x - q.x, p.y - q.y, p.z - q.z); r.set(result);}       //2 pt src, 1 pt dest  	

	public myPointf cloneMe(){myPointf retVal = new myPointf(this.x, this.y, this.z); return retVal;}  
	
	public float _L1Dist(myPointf q){return Math.abs((this.x - q.x)) + Math.abs((this.y - q.y)) + Math.abs((this.z - q.z)); }
	public static float _L1Dist(myPointf q, myPointf r){ return q._L1Dist(r);}
	
	public float _SqrDist(myPointf q){ return (((this.x - q.x)*(this.x - q.x)) + ((this.y - q.y)*(this.y - q.y)) + ((this.z - q.z)*(this.z - q.z))); }
	public static float _SqrDist(myPointf q, myPointf r){  return (((r.x - q.x) *(r.x - q.x)) + ((r.y - q.y) *(r.y - q.y)) + ((r.z - q.z) *(r.z - q.z)));}
	
	public float _dist(myPointf q){ return (float)Math.sqrt( ((this.x - q.x)*(this.x - q.x)) + ((this.y - q.y)*(this.y - q.y)) + ((this.z - q.z)*(this.z - q.z)) ); }
	public static float _dist(myPointf q, myPointf r){  return (float)Math.sqrt(((r.x - q.x) *(r.x - q.x)) + ((r.y - q.y) *(r.y - q.y)) + ((r.z - q.z) *(r.z - q.z)));}
	
	public float _dist(float qx, float qy, float qz){ return (float)Math.sqrt( ((this.x - qx)*(this.x - qx)) + ((this.y - qy)*(this.y - qy)) + ((this.z - qz)*(this.z - qz)) ); }
	public static float _dist(myPointf r, float qx, float qy, float qz){  return(float) Math.sqrt(((r.x - qx) *(r.x - qx)) + ((r.y - qy) *(r.y - qy)) + ((r.z - qz) *(r.z - qz)));}	
	
	public float[] asArray(){return new float[]{x,y,z};}
	public double[] asDblArray(){return new double[]{x,y,z};}
	
	public boolean clickIn(myPointf p, float eps) { return(_dist(p) < eps);}
	/**
	 * returns if this pttor is equal to passed pttor
	 * @param b myPointf to check
	 * @return whether they are equal
	 */
	public boolean equals(Object b){
		if (this == b) return true;
		if (!(b instanceof myPointf)) return false;
		myPointf v = (myPointf)b;
		return ((this.x == v.x) && (this.y == v.y) && (this.z == v.z));		
	}
	
	public String toStrCSV(){return toStrCSV("%.4f");}	
	public String toStrCSV(String fmt){return "" + String.format(fmt,this.x) + ", " + String.format(fmt,this.y) + ", " + String.format(fmt,this.z);}	
	public String toStrBrf(){return "|(" + String.format("%.4f",this.x) + ", " + String.format("%.4f",this.y) + ", " + String.format("%.4f",this.z)+")";}	
	public String toString(){return "|(" + String.format("%.4f",this.x) + ", " + String.format("%.4f",this.y) + ", " + String.format("%.4f",this.z)+")";}
}