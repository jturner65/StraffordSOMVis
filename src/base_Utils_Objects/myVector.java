package base_Utils_Objects;

public class myVector extends myPoint{
	public double sqMagn, magn;
	
	//vector constants available to all consumers of myVector
	public static final myVector ZEROVEC = new myVector(0,0,0);
	//public static final myVector UP	=	new myVector(0,1,0);
	//public static final myVector RIGHT = new myVector(1,0,0);
	//public static final myVector FORWARD = new myVector(0,0,1);
	public static final myVector UP	=	new myVector(0,0,1);
	public static final myVector RIGHT = new myVector(0,1,0);
	public static final myVector FORWARD = new myVector(1,0,0);
	
	public myVector(double _x, double _y, double _z){super(_x,_y,_z); this._mag();}         //constructor 3 args  
	public myVector(myVector p){ this(p.x, p.y, p.z); }                                                                                                           	//constructor 1 arg  
	public myVector(myVectorf p){ this(p.x, p.y, p.z); }                                                                                                           	//constructor 1 arg  
	public myVector(){ }//this(0,0,0);}                                                                                                                               //constructor 0 args
	public myVector(myPoint a, myPoint b){this(b.x-a.x,b.y-a.y,b.z-a.z);}			//vector from a->b
	public myVector(myPoint a){this(a.x,a.y,a.z);}			//vector from 0->a
	
	public myVector(myVector a, double _y, myVector b) {super(a,_y,b);this._mag();	}//interp cnstrctr
	public void clear() {super.clear();this.magn = 0; this.sqMagn=0;}
	public void set(double _x, double _y, double _z){ super.set(_x, _y, _z); this._mag(); }                                               //set 3 args 
	public void set(myVector p){ this.x = p.x; this.y = p.y; this.z = p.z;  this._mag();}                                                                   //set 1 args
	public void set(myPoint p, myPoint q){ this.x = q.x - p.x; this.y = q.y - p.y; this.z = q.z - p.z;  this._mag();}                                                                   //set 1 args
	public void set(double _x, double _y, double _z, double _sqMagn){ super.set(_x, _y, _z); this.sqMagn = _sqMagn; }                                                                     //set 3 args 
	
	public myVector _mult(double n){ super._mult(n); this._mag(); return this; }                                                     //_mult 3 args  
	public static myVector _mult(myVector p, double n){ myVector result = new myVector(p.x * n, p.y * n, p.z * n); return result;}                          //1 vec, 1 double
	public static myVector _mult(myVectorf p, double n){ myVector result = new myVector(p.x * n, p.y * n, p.z * n); return result;}                          //1 vec, 1 double
	public static myVector _ewise_mult(myVector p, myVector q){ myVector result = new myVector(p.x *q.x, p.y * q.y, p.z * q.z); return result;}                   //2 vec - point-wise
	public static void _mult(myVector p, myVector q, myVector r){ myVector result = new myVector(p.x *q.x, p.y * q.y, p.z * q.z); r.set(result);}           //2 vec src, 1 vec dest  
	
	public void _div(double q){super._div(q); this._mag();}  
	public static myVector _div(myVector p, double n){ if(n==0) return p; myVector result = new myVector(p.x / n, p.y / n, p.z / n); return result;}                          //1 pt, 1 double
	
	public void _add(double _x, double _y, double _z){ super._add(_x, _y, _z); this._mag(); }                                            //_add 3 args
	public void _add(myVector v){ this.x += v.x; this.y += v.y; this.z += v.z;  this._mag();  }                                                 //_add 1 arg  
	public void _add(myVectorf v){ this.x += v.x; this.y += v.y; this.z += v.z;  this._mag();  }                                                 //_add 1 arg  
	public static myVector _add(myVector p, myVector q){ myVector result = new myVector(p.x + q.x, p.y + q.y, p.z + q.z); return result;}                	//2 vec
	public static myVector _add(myPoint p, myVector q){ myVector result = new myVector(p.x + q.x, p.y + q.y, p.z + q.z); return result;}                	//2 vec
	public static void _add(myVector p, myVector q, myVector r){ myVector result = new myVector(p.x + q.x, p.y + q.y, p.z + q.z); r.set(result);}       	//2 vec src, 1 vec dest  
	
	public void _sub(double _x, double _y, double _z){ super._sub(_x, _y, _z);  this._mag(); }                                                                   //_sub 3 args
	public void _sub(myVector v){ this.x -= v.x; this.y -= v.y; this.z -= v.z;  this._mag(); }                                                                           //_sub 1 arg 
	public static myVector _sub(myPoint p, myPoint q){ return new myVector(p.x - q.x, p.y - q.y, p.z - q.z);}					             //2 pts or 2 vectors or any combo
	public static void _sub(myVector p, myVector q, myVector r){ myVector result = new myVector(p.x - q.x, p.y - q.y, p.z - q.z); r.set(result);}       //2 vec src, 1 vec dest  
	
	public double _mag(){ this.magn = Math.sqrt(this._SqMag()); return magn; }  
	public double _SqMag(){ this.sqMagn =  ((this.x*this.x) + (this.y*this.y) + (this.z*this.z)); return this.sqMagn; }  							//squared magnitude
	
	public void _scale(double _newMag){this._normalize()._mult(_newMag);}
	
	public myVector _normalize(){this._mag();if(magn==0){return this;}this.x = this.x/magn; this.y = this.y/magn; this.z = this.z/magn; _mag();return this;}
	//returns normal but doesn't modify v
	public static myVector _normalize(myVector v){double magn = v._mag(); if(magn==0){return v;} myVector newVec = new myVector( v.x / magn, v.y / magn, v.z / magn); return newVec;}// newVec._mag(); return newVec;}
	
	public double _norm(){return _mag();}
	public static double _L2Norm(myVector v){return Math.sqrt(v._SqMag());}
	public static double _L2SqNorm(myVector v){return v._SqMag();}
	
	public myVector _normalized(){double magn = this._mag(); myVector newVec = (magn == 0) ? (new myVector(0,0,0)) : (new myVector( this.x /= magn, this.y /= magn, this.z /= magn)); newVec._mag(); return newVec;}
	
	public myVector cloneMe(){myVector retVal = new myVector(this.x, this.y, this.z); return retVal;}  
		
	public double _SqrDist(myVector q){ return (((this.x - q.x)*(this.x - q.x)) + ((this.y - q.y)*(this.y - q.y)) + ((this.z - q.z)*(this.z - q.z))); }
	public double _SqrDist(myVectorf q){ return (((this.x - q.x)*(this.x - q.x)) + ((this.y - q.y)*(this.y - q.y)) + ((this.z - q.z)*(this.z - q.z))); }
	public static double _SqrDist(myVector q, myVector r){  return ((r.x - q.x)*(r.x - q.x)) + ((r.y - q.y)*(r.y - q.y)) + ((r.z - q.z)*(r.z - q.z));}
	
	public double _dist(myVector q){ return Math.sqrt( ((this.x - q.x)*(this.x - q.x)) + ((this.y - q.y)*(this.y - q.y)) + ((this.z - q.z)*(this.z - q.z)) ); }
	public static double _dist(myVector q, myVector r){  return Math.sqrt(((r.x - q.x) *(r.x - q.x)) + ((r.y - q.y) *(r.y - q.y)) + ((r.z - q.z) *(r.z - q.z)));}
	
	public static double _dist(myVector r, double qx, double qy, double qz){  return Math.sqrt(((r.x - qx) *(r.x - qx)) + ((r.y - qy) *(r.y - qy)) + ((r.z - qz) *(r.z - qz)));}	
	
	public myVector _cross(myVector b){ return new myVector((this.y * b.z) - (this.z*b.y), (this.z * b.x) - (this.x*b.z), (this.x * b.y) - (this.y*b.x));}		//cross product 
	public static myVector _cross(myVector a, myVector b){		return a._cross(b);}
	public static myVector _cross(double ax, double ay, double az, double bx, double by, double bz){		return new myVector((ay*bz)-(az*by), (az*bx)-(ax*bz), (ax*by)-(ay*bx));}
	
	
	public static myVector _elemMult(myVector a, myVector b){return new myVector(a.x*b.x, a.y*b.y, a.z*b.z);}
	public static myVector _elemDiv(myVector a, myVector b){return new myVector(a.x/b.x, a.y/b.y, a.z/b.z);}
	
	public static double _det3(myVector U, myVector V) {double udv = U._dot(V); return (Math.sqrt(U._dot(U)*V._dot(V) - (udv*udv))); };                                // U|V det product
	public static double _mixProd(myVector U, myVector V, myVector W) {return U._dot(myVector._cross(V,W)); };                                                 // (UxV)*W  mixed product, determinant - measures 6x the volume of the parallelapiped formed by myVectortors
	public double _dot(myVector b){return ((this.x * b.x) + (this.y * b.y) + (this.z * b.z));}																	//dot product
	public double _dot(myVectorf b){return ((this.x * b.x) + (this.y * b.y) + (this.z * b.z));}																	//dot product
	public static double _dot(myVector a, myVector b){		return a._dot(b);}
	
	public static double _angleBetween(myVector v1, myVector v2) {
		double 	_v1Mag = v1._mag(), 
				_v2Mag = v2._mag(), 
				dotProd = v1._dot(v2),
				cosAngle = dotProd/(_v1Mag * _v2Mag),
				angle = Math.acos(cosAngle);
		return angle;
	}//_angleBetween
	public static myVector _rotAroundAxis(myVector v1, myVector u){return _rotAroundAxis(v1, u, Math.PI*.5);}
	//rotate v1 around axis unit vector u, by give angle thet, around origin
	public static myVector _rotAroundAxis(myVector v1, myVector u, double thet){		
		double cThet = Math.cos(thet), sThet = Math.sin(thet), oneMC = 1-cThet,
				ux2 = u.x*u.x, uy2 = u.y*u.y, uz2 = u.z*u.z,
				uxy = u.x * u.y, uxz = u.x * u.z, uyz = u.y*u.z,
				uzS = u.z*sThet, uyS = u.y*sThet, uxS = u.x*sThet,
				uxzC1 = uxz *oneMC, uxyC1 = uxy*oneMC, uyzC1 = uyz*oneMC;
		//build rot matrix in vector def
		myVector res = new myVector(
				(ux2*oneMC+cThet) * v1.x + (uxyC1-uzS) 		* v1.y + (uxzC1+uyS) *v1.z,
				(uxyC1+uzS) 	  * v1.x + (uy2*oneMC+cThet)* v1.y + (uyzC1-uxS) *v1.z,
				(uxzC1-uyS) 	  * v1.x + (uyzC1+uxS)		* v1.y + (uz2*oneMC + cThet) * v1.z);
		
		return res;		
	}
	
	/**
	 * returns if this vector is equal to passed vector
	 * @param b vector to check
	 * @return whether they are equal
	 */
	public boolean equals(Object b){
		if (this == b) return true;
		if (!(b instanceof myVector)) return false;
		myVector v = (myVector)b;
		return ((this.x == v.x) && (this.y == v.y) && (this.z == v.z));		
	}				
	public String toStrCSV(){return toStrCSV("%.4f");}	
	public String toStrCSV(String fmt){return super.toStrCSV(fmt) + ", " + String.format(fmt,this.magn) + ", " + String.format(fmt,this.sqMagn);}	
	public String toString(){return super.toString()+ " | Mag:" + String.format("%.4f",this.magn)+ " | sqMag:" + String.format("%.4f",this.sqMagn);}
}//myVector