package base_Utils_Objects;

import java.util.Random;


//only works for primitives
public class Tuple<X,Y> implements Comparable<Tuple<X,Y>> { 
	private static final int[] seeds = new int[] {7919, 1699};
    public final X x;    public final Y y;  private final Float sqmag; private final int hash;
    public Tuple(X x, Y y) {    this.x = x;   this.y = y; sqmag = getSqMag(); hash= this.hashCode(); }
    public Tuple(Tuple<X,Y> _t) {   this( _t.x,_t.y);  }
    public String toString() {      return "(" + x + "," + y + ")";  }
    public boolean equals(Object _o) {  if (_o == null) {return false;} if (_o == this) { return true; } if (!(_o instanceof Tuple)){ return false; } Tuple<X,Y> o = (Tuple<X,Y>) _o;  return o.x.equals(this.x) && o.y.equals(this.y);  }
    public int hashCode() { 
//    	Random random = new Random(seeds[0] + x.hashCode());
//    	long result = random.nextInt();
//    	random.setSeed(seeds[1] + y.hashCode());
//    	result += random.nextInt();
//    	return (int) (result % Integer.MAX_VALUE);
    	
    	int result = 97 + ((x == null) ? 0 : x.hashCode());return 97 * result + ((y == null) ? 0 : y.hashCode());      	
    }
	private Float getSqMag(){if((x != null) && (y != null)) { return 1.0f*((x.hashCode()*x.hashCode()) + (y.hashCode()*y.hashCode()));} else {return null;}}
 	@Override
	public int compareTo(Tuple<X, Y> arg0) {//not a good measure - need to first use dist 		
 		if (this.hash== arg0.hash){return 0;}return (this.hash > arg0.hash) ? 1 : -1 ;
	}
}

//only works for primitives
class Triple<X,Y,Z> implements Comparable<Triple<X,Y,Z>> { 
	private static final int[] seeds = new int[] {7919, 1699, 5659};
	public final X x;    public final Y y; public final Z z; private final Float sqmag;
	public Triple(X x, Y y, Z z) {    this.x = x;   this.y = y; this.z = z; sqmag = getSqMag(); }
	public Triple(Triple<X,Y,Z> _t) {    this( _t.x,_t.y, _t.z);  }
	public String toString() {      return "(" + x + "," + y +"," + z + ")";  }
	public boolean equals(Object _o) {  if (_o == null) {return false;} if (_o == this) { return true; } if (!(_o instanceof Triple)){ return false; } Triple<X,Y,Z> o = (Triple<X,Y,Z>) _o;  return o.x.equals(this.x) && o.y.equals(this.y)&& o.z.equals(this.z);  }
	public int hashCode() { int result = 97 + ((x == null) ? 0 : x.hashCode()); result = 97 * result + ((y == null) ? 0 : y.hashCode()); return 97 * result + ((z == null) ? 0 : z.hashCode()); }
	private Float getSqMag(){if((x != null) && (y != null) && (z!= null)) { return 1.0f*((x.hashCode()*x.hashCode()) + (y.hashCode()*y.hashCode()) + (z.hashCode()*z.hashCode()));} else {return null;}}
	@Override
	public int compareTo(Triple<X,Y,Z> arg0) {
		return (this.hashCode() > arg0.hashCode() ? 1 : (this.hashCode() < arg0.hashCode() ? -1 : (this.equals(arg0) ? 0 : 1)));
	}
}//Triple<X,Y,Z>