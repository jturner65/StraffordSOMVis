package base_Utils_Objects.vectorObjs;

public class myMatrix {	  
	public double[][] m;
	
	public myMatrix(){  m = new double[4][4]; initMat();}	
	public void initMat(){  this.initMat(false);}	
	//initialize this matrix to be identity matrix
	//clear= true gives all 0's | clear= false gives identity
	void initMat(boolean clear){for (int row = 0; row < 4; ++row){  for (int col = 0; col < 4; ++col){m[row][col] =  ((row == col) && !clear)  ? 1 : 0 ;}}} 
	
	//multiplies this matrix by b, in order: [this] x [b]
	//returns result in result
	public myMatrix multMat(myMatrix b){
		double resultVal = 0;
		myMatrix result = new myMatrix();
		for (int row = 0; row < 4; ++row){for (int col = 0; col < 4; ++col){for (int k = 0; k < 4; k++){resultVal += this.m[row][k] * b.getValByIdx(k,col);}result.setValByIdx(row,col,resultVal); resultVal = 0;}}
		return result;  
	}//mult method
		
	//multiplies this matrix by vertex b, in order: [this] x [b]
	//returns result vertex in result
	public double[] multVert(double[] b){
		double resultVal;
		double[] result = new double[]{0,0,0,0};
		for (int row = 0; row < 4; ++row){resultVal = 0;for (int col = 0; col < 4; ++col){resultVal += this.m[row][col] * b[col];}	result[row] = resultVal;}//for row
		return result;  
	}//mult method
	
	//returns the transpose of this matrix - also inverse if rotation matrix
	public myMatrix transpose(){ 
		myMatrix result = new myMatrix();
		for (int row = 0; row < 4; ++row){for (int col = 0; col < 4; ++col){result.m[col][row] = this.m[row][col];}}
		return result;   
	}//transpose method

	public myMatrix inverse(){
		myMatrix result = this.InvertMe();
	  return result;    
	}//method invert
	public myMatrix adjoint(){
		myMatrix result = this.InvertMe();
		
		return result.transpose();
	}
	
	//------------- inversion code
	
	private myMatrix InvertMe(){
		double[] tmp = new double[12];   // temp array for pairs
		double[] src = new double[16];   // array of transpose source matrix
		double[] dst = new double[16];   //destination matrix, in array form
		double det = 0;       // determinant 
		myMatrix dstMat = new myMatrix();
		//convert this matrix to array form and set up source vector
		for(int row = 0; row < 4; ++row){ for(int col = 0; col < 4; ++col){ src[(4*col) + row] = this.m[row][col];	}}
		
		// calculate pairs for first 8 elements (cofactors)
		tmp[0] = src[10] * src[15];
		tmp[1] = src[11] * src[14];
		tmp[2] = src[9] * src[15];
		tmp[3] = src[11] * src[13];
		tmp[4] = src[9] * src[14];
		tmp[5] = src[10] * src[13];
		tmp[6] = src[8] * src[15];
		tmp[7] = src[11] * src[12];
		tmp[8] = src[8] * src[14];
		tmp[9] = src[10] * src[12];
		tmp[10] = src[8] * src[13];
		tmp[11] = src[9] * src[12];
		
		// calculate first 8 elements (cofactors) 	  
		dst[0] = tmp[0]*src[5] + tmp[3]*src[6] + tmp[4]*src[7];
		dst[0] -= tmp[1]*src[5] + tmp[2]*src[6] + tmp[5]*src[7];
		dst[1] = tmp[1]*src[4] + tmp[6]*src[6] + tmp[9]*src[7];
		dst[1] -= tmp[0]*src[4] + tmp[7]*src[6] + tmp[8]*src[7];
		dst[2] = tmp[2]*src[4] + tmp[7]*src[5] + tmp[10]*src[7];
		dst[2] -= tmp[3]*src[4] + tmp[6]*src[5] + tmp[11]*src[7];
		dst[3] = tmp[5]*src[4] + tmp[8]*src[5] + tmp[11]*src[6];
		dst[3] -= tmp[4]*src[4] + tmp[9]*src[5] + tmp[10]*src[6];
		dst[4] = tmp[1]*src[1] + tmp[2]*src[2] + tmp[5]*src[3];
		dst[4] -= tmp[0]*src[1] + tmp[3]*src[2] + tmp[4]*src[3];
		dst[5] = tmp[0]*src[0] + tmp[7]*src[2] + tmp[8]*src[3];
		dst[5] -= tmp[1]*src[0] + tmp[6]*src[2] + tmp[9]*src[3];
		dst[6] = tmp[3]*src[0] + tmp[6]*src[1] + tmp[11]*src[3];
		dst[6] -= tmp[2]*src[0] + tmp[7]*src[1] + tmp[10]*src[3];
		dst[7] = tmp[4]*src[0] + tmp[9]*src[1] + tmp[10]*src[2];
		dst[7] -= tmp[5]*src[0] + tmp[8]*src[1] + tmp[11]*src[2];
		
		// calculate pairs for second 8 elements (cofactors) 
		
		tmp[0] = src[2]*src[7];
		tmp[1] = src[3]*src[6];
		tmp[2] = src[1]*src[7];
		tmp[3] = src[3]*src[5];
		tmp[4] = src[1]*src[6];
		tmp[5] = src[2]*src[5];
		tmp[6] = src[0]*src[7];
		tmp[7] = src[3]*src[4];
		tmp[8] = src[0]*src[6];
		tmp[9] = src[2]*src[4];
		tmp[10] = src[0]*src[5];
		tmp[11] = src[1]*src[4];
		
		// calculate second 8 elements (cofactors)
		
		dst[8] = tmp[0]*src[13] + tmp[3]*src[14] + tmp[4]*src[15];
		dst[8] -= tmp[1]*src[13] + tmp[2]*src[14] + tmp[5]*src[15];
		dst[9] = tmp[1]*src[12] + tmp[6]*src[14] + tmp[9]*src[15];
		dst[9] -= tmp[0]*src[12] + tmp[7]*src[14] + tmp[8]*src[15];
		dst[10] = tmp[2]*src[12] + tmp[7]*src[13] + tmp[10]*src[15];
		dst[10]-= tmp[3]*src[12] + tmp[6]*src[13] + tmp[11]*src[15];
		dst[11] = tmp[5]*src[12] + tmp[8]*src[13] + tmp[11]*src[14];
		dst[11]-= tmp[4]*src[12] + tmp[9]*src[13] + tmp[10]*src[14];
		dst[12] = tmp[2]*src[10] + tmp[5]*src[11] + tmp[1]*src[9];
		dst[12]-= tmp[4]*src[11] + tmp[0]*src[9] + tmp[3]*src[10];
		dst[13] = tmp[8]*src[11] + tmp[0]*src[8] + tmp[7]*src[10];
		dst[13]-= tmp[6]*src[10] + tmp[9]*src[11] + tmp[1]*src[8];
		dst[14] = tmp[6]*src[9] + tmp[11]*src[11] + tmp[3]*src[8];
		dst[14]-= tmp[10]*src[11] + tmp[2]*src[8] + tmp[7]*src[9];
		dst[15] = tmp[10]*src[10] + tmp[4]*src[8] + tmp[9]*src[9];
		dst[15]-= tmp[8]*src[9] + tmp[11]*src[10] + tmp[5]*src[8];
		
		// calculate determinant 		
		det=src[0]*dst[0]+src[1]*dst[1]+src[2]*dst[2]+src[3]*dst[3];
		if(Math.abs(det) > .0000001){		
			for (int j = 0; j < 16; j++){ dst[j] /= det; }	    
			 //convert dst array to matrix
			for(int row = 0; row < 4; ++row){ for(int col = 0; col < 4; ++col){  dstMat.m[row][col] = dst[(4*row) + col];}}
		} else {
			System.out.println("uninvertible matrix -> det == 0");
		}
		return dstMat;
	}//invertme code	
	//end------------inversion code
	
	public double getValByIdx(int row, int col){   return m[row][col]; }  
	public void setValByIdx(int row, int col, double val){	   m[row][col] = val; }
	
		//writes over the first 3 cols of row given by row var with vector vals  
	public void setValByRow(int row, myVector vect){ this.m[row][0] = vect.x; this.m[row][1] = vect.y;      this.m[row][2] = vect.z; }//setValByRow
	 
	//makes a deep copy of this matrix, which it returns
	public myMatrix clone(){
		myMatrix newMat = new myMatrix();
		for (int row = 0; row < 4; ++row){ for (int col = 0; col < 4; ++col){newMat.m[row][col] = this.m[row][col];}}
		return newMat;
	}
	
	public String toString(){
	    String result = "", tmp2str = "",tmpString;
	    for (int row = 0; row < 4; ++row){
	    	result += "[";
	    	for (int col = 0; col < 4; ++col){   tmp2str = "" + m[row][col]; if (col != 3) {tmp2str += ", ";} result += tmp2str;}
	    	tmpString = "]";  if (row != 3) { tmpString += "\n"; }
	    	result += tmpString;
    	}//for row	   
	    return result;
	}//toString method
  
}//class matrix