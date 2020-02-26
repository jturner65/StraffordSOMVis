package strafford_SOM_PKG.straff_Features.featureCalc;

import java.util.ArrayList;

import base_JavaProjTools_IRender.base_Render_Interface.IRenderInterface;
//import base_UI_Objects.my_procApplet;

//this class will hold analysis information for calculations to more clearly understand the results of the current calc object
public class Straff_CalcAnalysis{//per JPWeightEquation analysis of data
		//corresponding eq - get all pertinent info from this object
	protected Straff_JPWeightEquation eq;	
		//totals seen across all examples per individual calc components(prospect, opt, order, link, etc);sum of sq value, for variance/std calc
	protected float[] vals, valSq;
	
	//calculated statistics 
	protected float[][] analysisCalcStats;
	//calculated totals of statistic values, to get appropriate ratios
	protected float[] ttlCalcStats_Vis;
	protected static final int
		ratioIDX = 0,			//ratio of total wt contribution for JP for particular eq-type value (i.e. orders, links, etc.)
		meanIDX = 1,			//over all examples for particular JP, including opt-outs
		meanOptIDX = 2,			//only include non-opt-out data
		stdIDX = 3,				//over all examples incl opt-outs
		stdOptIDX = 4;			//only non-opt-out
	protected static final int numCalcStats = 5;
	//titles of each stat-of-interest disp bar
	protected static final String[] calcStatTitles = new String[] {"Ratios", "Means", "Means w/Opts", "Stds","Stds w/Opts"};
	//descriptive text under each detail display bar
	protected String[][] calcStatDispDetail;
	//perStat perCalcEqType Descriptive string of value being represented in bar
	protected String[][] analysisCalcValStrs;
	//description for legend
	protected String[] legendDatStrAra;
		//workspace used by calc object to hold values - all individual values calculated - this is so we can easily aggregate analysis results to calc object	
	protected float[] workSpace;
		//total seen across all individual calcs, across all examples; mean value sent per non-opt, sqVal for std calc; stdVal of totals
	protected float ttlVal, ttlSqVal;//, ttlMeansVec_vis, ttlStdsVec_vis, ttlMeansOptVec_vis, ttlStdsOptVec_vis;
		//number of eqs processed - increment on total
	protected int numExamplesWithJP = 0;
	protected int numExamplesNoOptOut = 0;
		//counts of each type
	protected int[] eqTypeCount;

	//array of analysis components for string display
	protected ArrayList<String> analysisRes;
	//how far to move down for text
	protected static float txtYOff = 10.0f;
	
	protected static float[] legendSizes;
	//disp idx of this calc;jpIDX corresponding to owning eq - NOTE this is either the index in the ftr vector, or it is the index in the list of all jps
	protected final int[] jpIDXara;
	protected int[] dispIDXAra;
	//
	public Straff_CalcAnalysis(Straff_JPWeightEquation _eq, int[] _jpIDX) {
		eq=_eq;reset();
		jpIDXara=_jpIDX;
		dispIDXAra = new int[] {jpIDXara[0]%5,jpIDXara[1]%5} ;
		legendSizes=new float[Straff_JPWeightEquation.numEqs];
		for(int i=0;i<Straff_JPWeightEquation.numEqs;++i) {legendSizes[i]= 1.0f/Straff_JPWeightEquation.numEqs;}
	}//ctor
	//reset this calc analysis object
	public synchronized void reset() {
		vals = new float[Straff_JPWeightEquation.numEqs];
		eqTypeCount = new int[Straff_JPWeightEquation.numEqs];
		valSq = new float[vals.length];
		workSpace = new float[vals.length];
		legendDatStrAra = new String[vals.length];
		numExamplesWithJP = 0;
		numExamplesNoOptOut = 0;
		ttlVal=0.0f;
		ttlSqVal=0.0f;
		//per stat type; per calc val type
		analysisCalcStats = new float[numCalcStats][];
		calcStatDispDetail = new String[numCalcStats][];
		ttlCalcStats_Vis = new float[numCalcStats];
		for(int i=0; i<numCalcStats;++i) {		analysisCalcStats[i] = new float[vals.length];}			
		analysisRes = new ArrayList<String>();
	}//reset()
	
	//overwrites old workspace calcs
	public synchronized void setWSVal(int idx, float val) {
		workSpace[idx]=val;		
		if (val != 0.0f) {eqTypeCount[idx]++;}		
	}
	
	//add values for a particular calc run - returns calc total
	public synchronized float getFtrValFromCalcs(int optCoeffIDX, float optOutValCk) {
			//if below is true, then this means this JP's opt out value was set to sentinel val to allow for calculations but ultimately to set the weight of this jp's contribution to 0
		boolean optOut = workSpace[optCoeffIDX]==optOutValCk;
		++numExamplesWithJP;		//if optOut is true, then this increases # of examples, but all values will be treated as 0
			//opt result means all values are cleared out (user opted out of this specific JP) so only increment numExamplesWithJP
		if (optOut) {return 0.0f;	}
		++numExamplesNoOptOut;
			//res is per record result - this is result of eq for calculation, used by weight.
		float res = 0;
			//add all results and also add all individual results to vals
		for (int i=0;i<vals.length;++i) {
			res += workSpace[i];
			vals[i] += workSpace[i];
			valSq[i] += workSpace[i] * workSpace[i];//variance== (sum ( vals^2))/N  - mean^2
		}
		ttlVal += res;
		ttlSqVal += res*res;
		return res;		
	}//addCalcsToVals	

	//aggregate collected values and calculate all relevant statistics
	public void aggregateCalcVals() {
		//per stat type; per calc val type
		analysisCalcStats = new float[numCalcStats][];
		analysisCalcValStrs = new String[numCalcStats][];
		calcStatDispDetail = new String[numCalcStats][];
		ttlCalcStats_Vis = new float[numCalcStats];
		legendDatStrAra = new String[vals.length];
		for(int i=0; i<numCalcStats;++i) {		analysisCalcStats[i] = new float[vals.length];analysisCalcValStrs[i] = new String[vals.length];}			
		analysisRes = new ArrayList<String>();
		
		String perCompMuStd = "";
		if(ttlVal == 0.0f) {return;}
		for (int i=0;i<vals.length;++i) {
			analysisCalcStats[ratioIDX][i] = vals[i]/ttlVal;
			analysisCalcStats[meanIDX][i] = vals[i]/numExamplesNoOptOut;
			analysisCalcStats[meanOptIDX][i] = vals[i]/numExamplesWithJP;		//counting opt-out records
			analysisCalcStats[stdIDX][i] = (float) Math.sqrt((valSq[i]/numExamplesNoOptOut) - (analysisCalcStats[meanIDX][i]*analysisCalcStats[meanIDX][i]));//E[X^2] - E[X]^2
			analysisCalcStats[stdOptIDX][i] = (float) Math.sqrt((valSq[i]/numExamplesWithJP) - (analysisCalcStats[meanOptIDX][i]*analysisCalcStats[meanOptIDX][i]));		
			perCompMuStd += "|Mu:"+String.format("%.5f",analysisCalcStats[meanIDX][i])+"|Std:"+String.format("%.5f",analysisCalcStats[stdIDX][i]);			
			legendDatStrAra[i] = Straff_JPWeightEquation.calcNames[i] + " : "+ eqTypeCount[i]+" exmpls.";			
		}
		for (int i=0;i<analysisCalcStats.length;++i) {
			ttlCalcStats_Vis[i]=0.0f;
			for (int j=0;j<analysisCalcStats[i].length;++j) {		
				ttlCalcStats_Vis[i] += analysisCalcStats[i][j];		
				analysisCalcValStrs[i][j] = String.format("%.5f", analysisCalcStats[i][j]);
			}
		}
		
		float ratioOptOut = 1 - (1.0f*numExamplesNoOptOut)/numExamplesWithJP;			//ratio of all examples that have opted out with 0 ttl contribution to jp in ftr
		float ttlMeanNoOpt = ttlVal/numExamplesNoOptOut;
		float ttlMeanWithOpt = ttlVal/numExamplesWithJP;
		float ttlStdNoOpt = (float) Math.sqrt((ttlSqVal/numExamplesNoOptOut)  - (ttlMeanNoOpt * ttlMeanNoOpt));
		float ttlStdWithOpt = (float) Math.sqrt((ttlSqVal/numExamplesWithJP)  - (ttlMeanWithOpt * ttlMeanWithOpt));
		calcStatDispDetail[ratioIDX] = new String[] {String.format("Ratio of Opts To Ttl : %.5f",ratioOptOut), String.format("# of examples : %05d",numExamplesWithJP),String.format("# of ex w/o Opt out : %05d",numExamplesNoOptOut)};
		calcStatDispDetail[meanIDX] = new String[] {String.format("Mean : %.5f",ttlMeanNoOpt)};
		calcStatDispDetail[meanOptIDX] = new String[] {String.format("Mean w/opts : %.5f", ttlMeanWithOpt)};
		calcStatDispDetail[stdIDX] = new String[] {String.format("Stds : %.5f",ttlStdNoOpt)};
		calcStatDispDetail[stdOptIDX] = new String[] {String.format("Std w/opts : %.5f",ttlStdWithOpt)};		
		
		analysisRes.add("FTR IDX : "+String.format("%03d", jpIDXara[0])+"ALL JP IDX : "+String.format("%03d", jpIDXara[1])+"|JP : "+String.format("%03d", eq.jp)+"|% opt:"+String.format("%.5f",ratioOptOut)
					+"|MU : " + String.format("%.5f",ttlMeanNoOpt)+"|Std : " + String.format("%.5f",ttlStdNoOpt) 
					+"|MU w/opt : " +String.format("%.5f",ttlMeanWithOpt)+"|Std w/opt : " +String.format("%.5f",ttlStdWithOpt));
		analysisRes.add(perCompMuStd);
	}//aggregateCalcVals
	
	
	public void showOffsetText2D(IRenderInterface p, float d, int tclr, String txt){
		p.setColorValFill(tclr, 255);p.setColorValStroke(tclr, 255);
		p.showText(txt, d, d,0); 
	}

		
	//this will display a vertical bar corresponding to the performance of the analyzed calculation.
	//each component of calc object will have a different color
	//height - the height of the bar.  start each vertical bar at upper left corner, put text beneath bar
	public void drawFtrVec(IRenderInterface p, float height, float width, int eqDispType, boolean selected){
		p.pushMatState();
		float rCompHeight, rYSt = 0.0f;
		for(int i =0;i<analysisCalcStats[ratioIDX].length;++i) {
			p.setColorValFill(IRenderInterface.gui_LightRed+i, 255);
			rCompHeight = height * analysisCalcStats[ratioIDX][i];
			p.drawRect(new float[] {0.0f, rYSt, width, rCompHeight});
			rYSt+=rCompHeight;
		}
		if (selected) {
			p.setColorValFill(IRenderInterface.gui_White, 100);
			p.drawRect(new float[] {-1.0f, -1.0f, width+2, height+2});
		} 

		p.translate(0.0f, height+txtYOff, 0.0f);
		p.translate(0.0f, dispIDXAra[eqDispType]*txtYOff, 0.0f);
		showOffsetText2D(p,0.0f, IRenderInterface.gui_White, ""+eq.jp);
		p.popMatState();
	}//drawFtrVec
	
	//draw vertical bar describing per-comp values with
	protected void drawDetailFtrVec(IRenderInterface p, float height, float width, float[] vals, float denom, String valTtl, String[] dispStrAra, String[] valDesc) {
		p.pushMatState();
			p.translate(0.0f, txtYOff, 0.0f);
			showOffsetText2D(p,0.0f, IRenderInterface.gui_White, valTtl);
			p.translate(0.0f, txtYOff, 0.0f);
			p.pushMatState();
				float rCompHeight, rYSt = 0.0f, htMult = height/denom;
				for(int i =0;i<vals.length;++i) {
					if (vals[i] > 0.0f) {
						p.setColorValFill(IRenderInterface.gui_LightRed+i, 255);
						rCompHeight = htMult * vals[i];
						p.drawRect(new float[] {0.0f, rYSt, width, rCompHeight});
						rYSt+=rCompHeight;
					}
				}
				rCompHeight = 0.0f;
				rYSt = 0.0f;
				//make sure text for small boxes isn't overwritten by next box
				for(int i =0;i<vals.length;++i) {
					if (vals[i] > 0.0f) {
						rCompHeight = htMult * vals[i];
						p.pushMatState();
						p.translate(10.0f, rYSt+(rCompHeight/2.0f)+5, 0.0f);
						if(null!= dispStrAra[i]) {			showOffsetText2D(p,0.0f, IRenderInterface.gui_Black, dispStrAra[i]);}
						p.popMatState();
						rYSt+=rCompHeight;
					}
				}		
			p.popMatState();			
			p.translate(0.0f, height+txtYOff, 0.0f);
			if(valDesc != null) {
				for(String s : valDesc) {
					showOffsetText2D(p,0.0f, IRenderInterface.gui_White, s);
					p.translate(0.0f, txtYOff, 0.0f);
				}
			}
			//move down and print out relevant text
		p.popMatState();	
	}//drawSpecificFtrVecWithText

	//draw a single ftr vector as a wide bar; include text for descriptions
	//width is per bar
	public void drawIndivFtrVec(IRenderInterface p, float height, float width){
		p.pushMatState();
		//title here?
		showOffsetText2D(p,0.0f, IRenderInterface.gui_White, "Calc Values for ftr idx : " +jpIDXara[0] +"," +jpIDXara[1]+ " jp "+eq.jp + " : " + eq.jpName);//p.drawText("Calc Values for ftr idx : " +eq.jpIdx + " jp "+eq.jp, 0, 0, 0, p.gui_White);
		p.translate(0.0f, txtYOff, 0.0f);
		for(int i=0;i<analysisCalcStats.length;++i) {
			drawDetailFtrVec(p,height,width, analysisCalcStats[i], ttlCalcStats_Vis[i], calcStatTitles[i], analysisCalcValStrs[i], calcStatDispDetail[i]);
			p.translate(width*1.5f, 0.0f, 0.0f);
		}	
		drawDetailFtrVec(p,height,width, legendSizes, 1.0f, "Legend", legendDatStrAra, new String[] {});
		//drawLegend(p,height,width, legendSizes, 1.0f);
		p.popMatState();	
	}//drawIndivFtrVec

	//return basic stats for this calc in tight object
	public ArrayList<String> getCalcRes() {return analysisRes;	}//calcRes
	
	
}//calcAnalysis