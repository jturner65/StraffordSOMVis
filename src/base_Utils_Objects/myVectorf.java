package base_Utils_Objects;

public class myVectorf extends myPointf{
	public float sqMagn, magn;
	
	//vector constants available to all consumers of myVectorf
	public static final myVectorf ZEROVEC = new myVectorf(0,0,0);
	//public static final myVectorf UP	=	new myVectorf(0,1,0);
	//public static final myVectorf RIGHT = new myVectorf(1,0,0);
	//public static final myVectorf FORWARD = new myVectorf(0,0,1);
	public static final myVectorf UP	=	new myVectorf(0,0,1);
	public static final myVectorf RIGHT = new myVectorf(0,1,0);
	public static final myVectorf FORWARD = new myVectorf(1,0,0);
	
	public myVectorf(float _x, float _y, float _z){super(_x,_y,_z); this._mag();}         //constructor 3 args  
	public myVectorf(double _x, double _y, double _z){this((float)_x,(float)_y,(float)_z); }         //constructor with doubles
	public myVectorf(myVectorf p){ this(p.x, p.y, p.z); }                                                                                                           	//constructor 1 arg  
	public myVectorf(myVector p){ this((float)p.x, (float)p.y, (float)p.z); }                                                                                                           	//constructor 1 arg  
	public myVectorf(){ }//this(0,0,0);}                                                                                                                               //constructor 0 args
	public myVectorf(myPointf a, myPointf b){this(b.x-a.x,b.y-a.y,b.z-a.z);}			//vector from a->b
	public myVectorf(myPoint a, myPointf b){this(b.x-a.x,b.y-a.y,b.z-a.z);}			//vector from a->b
	public myVectorf(myPoint a, myPoint b){this(b.x-a.x,b.y-a.y,b.z-a.z);}			//vector from a->b
	public myVectorf(myPointf a){this(a.x,a.y,a.z);}			//vector from 0->a
	
	public myVectorf(myVectorf a, float _y, myVectorf b) {super(a,_y,b);this._mag();	}//interp cnstrctr
	public void clear() {super.clear();this.magn = 0; this.sqMagn=0;}
	public void set(float _x, float _y, float _z){ super.set(_x, _y, _z); this._mag(); }                                               //set 3 args 
	public void set(double _x, double _y, double _z){ this.set((float)_x,(float)_y,(float)_z); }                                               //set 3 args 
	public void set(myVectorf p){ this.x = p.x; this.y = p.y; this.z = p.z;  this._mag();}                                                                   //set 1 args
	public void set(myPointf p, myPointf q){ this.x = q.x - p.x; this.y = q.y - p.y; this.z = q.z - p.z;  this._mag();}                                                                   //set 1 args
	public void set(float _x, float _y, float _z, float _sqMagn){ super.set(_x, _y, _z); this.sqMagn = _sqMagn; }                                                                     //set 3 args 
	
	public myVectorf _mult(float n){ super._mult(n); this._mag(); return this; }                                                     //_mult 3 args  
	public myVectorf _mult(double n){ super._mult((float)n); this._mag(); return this; }                                                     //_mult 3 args  
	public static myVectorf _mult(myVectorf p, float n){ myVectorf result = new myVectorf(p.x * n, p.y * n, p.z * n); return result;}                          //1 vec, 1 float
	public static myVectorf _mult(myVectorf p, double n){ myVectorf result = new myVectorf(p.x * n, p.y * n, p.z * n); return result;}                          //1 vec, 1 float
	public static myVectorf _mult(myVectorf p, myVectorf q){ myVectorf result = new myVectorf(p.x *q.x, p.y * q.y, p.z * q.z); return result;}                   //2 vec
	public static void _mult(myVectorf p, myVectorf q, myVectorf r){ myVectorf result = new myVectorf(p.x *q.x, p.y * q.y, p.z * q.z); r.set(result);}           //2 vec src, 1 vec dest  
	
	public void _div(float q){super._div(q); this._mag();}  
	public static myVectorf _div(myVectorf p, float n){ if(n==0) return p; myVectorf result = new myVectorf(p.x / n, p.y / n, p.z / n); return result;}                          //1 pt, 1 float
	
	public void _add(float _x, float _y, float _z){ super._add(_x, _y, _z); this._mag(); }                                            //_add 3 args
	public void _add(myVectorf v){ this.x += v.x; this.y += v.y; this.z += v.z;  this._mag();  }                                                 //_add 1 arg  
	public static myVectorf _add(myVectorf p, myVectorf q){ myVectorf result = new myVectorf(p.x + q.x, p.y + q.y, p.z + q.z); return result;}                	//2 vec
	public static myVectorf _add(myPointf p, myVectorf q){ myVectorf result = new myVectorf(p.x + q.x, p.y + q.y, p.z + q.z); return result;}                	//2 vec
	public static void _add(myVectorf p, myVectorf q, myVectorf r){ myVectorf result = new myVectorf(p.x + q.x, p.y + q.y, p.z + q.z); r.set(result);}       	//2 vec src, 1 vec dest  
	
	public void _sub(float _x, float _y, float _z){ super._sub(_x, _y, _z);  this._mag(); }                                                                   //_sub 3 args
	public void _sub(myVectorf v){ this.x -= v.x; this.y -= v.y; this.z -= v.z;  this._mag(); }                                                                           //_sub 1 arg 
	public static myVectorf _sub(myPointf p, myPointf q){ return new myVectorf(p.x - q.x, p.y - q.y, p.z - q.z);}					             //2 pts or 2 vectors or any combo
	public static void _sub(myVectorf p, myVectorf q, myVectorf r){ myVectorf result = new myVectorf(p.x - q.x, p.y - q.y, p.z - q.z); r.set(result);}       //2 vec src, 1 vec dest  
	
	public float _mag(){ this.magn = (float)Math.sqrt(this._SqMag()); return magn; }  
	public float _SqMag(){ this.sqMagn =  ((this.x*this.x) + (this.y*this.y) + (this.z*this.z)); return this.sqMagn; }  							//squared magnitude
	
	public void _scale(float _newMag){this._normalize()._mult(_newMag);}
	
	public myVectorf _normalize(){this._mag();if(magn==0){return this;}this.x = this.x/magn; this.y = this.y/magn; this.z = this.z/magn; _mag();return this;}
	//public static myVectorf _normalize(myVectorf v){float magn = v._mag(); if(magn==0){return v;} myVectorf newVec = new myVectorf( v.x /= magn, v.y /= magn, v.z /= magn); newVec._mag(); return newVec;}
	public static myVectorf _normalize(myVectorf v){double magn = v._mag(); if(magn==0){return v;} myVectorf newVec = new myVectorf( v.x / magn, v.y / magn, v.z / magn);return newVec;}// newVec._mag(); return newVec;}
	
	public float _norm(){return _mag();}
	public static float _L2Norm(myVectorf v){return (float)Math.sqrt(v._SqMag());}
	public static float _L2SqNorm(myVectorf v){return v._SqMag();}
	
	public myVectorf _normalized(){float magn = this._mag(); myVectorf newVec = (magn == 0) ? (new myVectorf(0,0,0)) : (new myVectorf( this.x /= magn, this.y /= magn, this.z /= magn)); newVec._mag(); return newVec;}
	
	public myVectorf cloneMe(){myVectorf retVal = new myVectorf(this.x, this.y, this.z); return retVal;}  
		
	public float _SqrDist(myVectorf q){ return (((this.x - q.x)*(this.x - q.x)) + ((this.y - q.y)*(this.y - q.y)) + ((this.z - q.z)*(this.z - q.z))); }
	public static float _SqrDist(myVectorf q, myVectorf r){  return ((r.x - q.x)*(r.x - q.x)) + ((r.y - q.y)*(r.y - q.y)) + ((r.z - q.z)*(r.z - q.z));}
	
	public float _dist(myVectorf q){ return (float)Math.sqrt( ((this.x - q.x)*(this.x - q.x)) + ((this.y - q.y)*(this.y - q.y)) + ((this.z - q.z)*(this.z - q.z)) ); }
	public static float _dist(myVectorf q, myVectorf r){  return (float)Math.sqrt(((r.x - q.x) *(r.x - q.x)) + ((r.y - q.y) *(r.y - q.y)) + ((r.z - q.z) *(r.z - q.z)));}
	
	public static float _dist(myVectorf r, float qx, float qy, float qz){  return (float)Math.sqrt(((r.x - qx) *(r.x - qx)) + ((r.y - qy) *(r.y - qy)) + ((r.z - qz) *(r.z - qz)));}	
	
	public myVectorf _cross(myVectorf b){ return new myVectorf((this.y * b.z) - (this.z*b.y), (this.z * b.x) - (this.x*b.z), (this.x * b.y) - (this.y*b.x));}		//cross product 
	public static myVectorf _cross(myVectorf a, myVectorf b){		return a._cross(b);}
	public static myVectorf _cross(float ax, float ay, float az, float bx, float by, float bz){		return new myVectorf((ay*bz)-(az*by), (az*bx)-(ax*bz), (ax*by)-(ay*bx));}
	
	
	public static myVectorf _elemMult(myVectorf a, myVectorf b){return new myVectorf(a.x*b.x, a.y*b.y, a.z*b.z);}
	public static myVectorf _elemDiv(myVectorf a, myVectorf b){return new myVectorf(a.x/b.x, a.y/b.y, a.z/b.z);}
	
	public static float _det3(myVectorf U, myVectorf V) {float udv = U._dot(V); return (float)(Math.sqrt(U._dot(U)*V._dot(V) - (udv*udv))); };                                // U|V det product
	public static float _mixProd(myVectorf U, myVectorf V, myVectorf W) {return U._dot(myVectorf._cross(V,W)); };
	public float _dot(myVectorf b){return ((this.x * b.x) + (this.y * b.y) + (this.z * b.z));}
	public static float _dot(myVectorf a, myVectorf b){		return a._dot(b);}
	
	public static float _angleBetween(myVectorf v1, myVectorf v2) {
		float 	_v1Mag = v1._mag(), 
				_v2Mag = v2._mag(), 
				dotProd = v1._dot(v2),
				cosAngle = dotProd/(_v1Mag * _v2Mag),
				angle = (float)(Math.acos(cosAngle));
		return angle;
	}//_angleBetween
	public static myVectorf _rotAroundAxis(myVectorf v1, myVectorf u){return _rotAroundAxis(v1, u, (float)Math.PI*.5f);}
	//rotate v1 around axis unit vector u, by give angle thet, around origin
	public static myVectorf _rotAroundAxis(myVectorf v1, myVectorf u, float thet){		
		float cThet = (float)(Math.cos(thet)), sThet = (float)(Math.sin(thet)), oneMC = 1-cThet,
				ux2 = u.x*u.x, uy2 = u.y*u.y, uz2 = u.z*u.z,
				uxy = u.x * u.y, uxz = u.x * u.z, uyz = u.y*u.z,
				uzS = u.z*sThet, uyS = u.y*sThet, uxS = u.x*sThet,
				uxzC1 = uxz *oneMC, uxyC1 = uxy*oneMC, uyzC1 = uyz*oneMC;
		//build rot matrix in vector def
		myVectorf res = new myVectorf(
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
		if (!(b instanceof myVectorf)) return false;
		myVectorf v = (myVectorf)b;
		return ((this.x == v.x) && (this.y == v.y) && (this.z == v.z));		
	}				
	public String toStrCSV(){return toStrCSV("%.4f");}	
	public String toStrCSV(String fmt){return super.toStrCSV(fmt) + ", " + String.format(fmt,this.magn) + ", " + String.format(fmt,this.sqMagn);}	
	public String toString(){return super.toString()+ " | Mag:" + String.format("%.4f",this.magn)+ " | sqMag:" + String.format("%.4f",this.sqMagn);}
}//myVectorf




