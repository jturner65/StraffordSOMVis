package base_Utils_Objects;

import base_Utils_Objects.vectorObjs.myPoint;
import base_Utils_Objects.vectorObjs.myVector;

/**
 * mathematical functions and constants that might be of use in some applications
 * @author john
 *
 */
public class MyMathUtils {
	public static final double 
		halfPi = .5*Math.PI,
		twoPi = 2.0*Math.PI,
		threeQtrPI = .75 * Math.PI,
		sqrt2 = Math.sqrt(2.0),
		invSqrt2 = .5 * sqrt2,
		sqrt3 = Math.sqrt(3.0),
		invSqrt3 = 1.0/sqrt3;

	public static final float 
		halfPi_f = (float) halfPi,
		twoPi_f = (float) twoPi,
		threeQtrPI_f = (float) threeQtrPI,
		sqrt2_f = (float) sqrt2,
		invSqrt2_f = (float) invSqrt2,
		sqrt3_f = (float) sqrt3,
		invSqrt3_f = (float) invSqrt3;

	//shouldn't be instanced
	private MyMathUtils() {	}
	
	/**
	 * find distance of P to line determined by AB
	 * @param P
	 * @param A
	 * @param B
	 * @return
	 */
	public synchronized static double distToLine(myPoint P, myPoint A, myPoint B) {
		myVector AB = new myVector(A,B),AP = new myVector(A,P);
		AB._normalize();
		double udv = AB._dot(AP); //project AP onto line		
		double resSq = AP.sqMagn - (udv*udv);		//AB mag is 1 so can be ignored
		if(resSq < 0) {return 0;}
		return Math.sqrt(resSq); 
	};		//MAY RETURN NAN IF point P is on line
		
	/**
	 * return the projection point of P on line determined by AB between A and B
	 * @param P point to investigate
	 * @param A,B line seg endpoints
	 * @return 
	 */	
	public synchronized static myPoint projectionOnLine(myPoint P, myPoint A, myPoint B) {
		myVector AB = new myVector(A,B), AP = new myVector(A,P);
		return new myPoint(A,AB._dot(AP)/(AB._dot(AB)),AB);
	}
	/**
	 * return true if P orthogonally projects onto line determined by AB between A and B
	 * @param P point to investigate
	 * @param A,B line seg endpoints
	 * @return
	 */
	public synchronized static boolean projectsBetween(myPoint P, myPoint A, myPoint B) {
		myVector AP = new myVector(A,P), AB = new myVector(A,B);
		if(AP._dot(AB) <= 0) {return false;}		//if not greater than 0 than won't project onto AB - past A away from segment
		myVector BP = new myVector(B,P), BA = new myVector(B,A);		
		return BP._dot(BA)>0 ; 						//if not greater than 0 than won't project onto AB - past B away from segment
	}
	
	/**
	 * Calculate normal to planed described by triangle ABC, non-normalized (proportional to area)
	 * @param A, B, C verts of triangle
	 * @return
	 */
	public myVector normToPlane(myPoint A, myPoint B, myPoint C) {
		return myVector._cross(new myVector(A,B),new myVector(A,C)); 
	};   // normal to triangle (A,B,C), not normalized (proportional to area)
	

	/**
	 * quake inv sqrt calc - about 30% faster than 
	 * @param x
	 * @return
	 */
    public synchronized static float invSqrtFloat(float x){
        float xhalf = x * 0.5f;
        int i = Float.floatToIntBits(x);
        i = 0x5f3759df - (i >> 1);
        x = Float.intBitsToFloat(i);
        x *= (1.5f - (xhalf * x * x));   // newton iter
        return x;
    }
    /**
     * double version of quake sqrt approx - 2x as fast as Math.sqrt
     * @param x
     * @return
     */
    public static double invSqrtDouble(double x){    	
        double xhalf = x * 0.5;
        long i = Double.doubleToLongBits(x);
        i = 0x5fe6eb50c7b537a9L - (i >> 1);
        x = Double.longBitsToDouble(i);
        x *= (1.5 - (xhalf * x * x));   // newton iter
        return x;
    }
    
    /**
     * recursive factorial formulation
     * @param x
     * @return
     */
    public static int fact(int x) {
    	if(x < 2) {return 1;} if (x==2) {return 2;}
    	return x * fact(x-1);
    }
    
    /**
     * n choose k == n!/(k! * (n-k)!) - ways to choose k items from a set of size n
     * @param n top - size of pop
     * @param k bottom - size of choice
     * @return
     */
    public static int choose(int n, int k) {		//entry point
    	if(k>n) {return 0;} if (k==n) {return 1;}
    	return _choose(n,k);
    }
    private static int _choose(int n, int k) {		//recursive formulation
    	if((k==0) || (k==n)) {return 1;}
    	return (_choose(n-1,k-1) + _choose (n-1, k));
    }

}//math utils

