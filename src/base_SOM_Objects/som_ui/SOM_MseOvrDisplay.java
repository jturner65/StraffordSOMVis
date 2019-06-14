package base_SOM_Objects.som_ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;

import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.ExDataType;
import base_SOM_Objects.som_examples.SOMMapNode;
import base_UI_Objects.my_procApplet;
import base_Utils_Objects.vectorObjs.myPointf;

public abstract class SOM_MseOvrDisplay {
	protected SOM_MapManager mapMgr;
	protected float dispThesh;
	protected String[] mseLabelAra;
	protected float[] mseLabelDims;
	protected boolean display = false;
	protected myPointf mapLoc;
	protected int[] nodeClrs;	
	
	public SOM_MseOvrDisplay(SOM_MapManager _mapMgr, float _dispThesh) {
		mapMgr=_mapMgr;
		initAllCtor(_dispThesh);
		mapLoc = new myPointf(0,0,0);
		nodeClrs = setNodeColors(); 
	}//ctor
	
	protected abstract int[] setNodeColors(); 
	
	
	private void initAllCtor(float _thresh) {
		//needs to be here since jpJpgrpMon won't exist when this is first made
		dispThesh = _thresh;	
		mseLabelAra = new String[0];
		mseLabelDims = new float[0];
		initAll_Indiv();
	}//initAllCtor
	/**
	 * instancing-specific initialization called for every data change for mouse object
	 */
	protected abstract void initAll_Indiv();
	
	//build display label arraylist from passed map of float-name labels, using line as header/desc
	private int buildDispArrayList(ArrayList<String> _mseLblDat, TreeMap<Float, ArrayList<String>> valsToDisp, String line, int valsPerLine) {
		int longestLine = line.length();
		if (valsToDisp.size()== 0) {
			line += "None";
			_mseLblDat.add(line);
			longestLine = longestLine >= line.length() ? longestLine : line.length();
		}
		else {
			_mseLblDat.add(line);
			longestLine = longestLine >= line.length() ? longestLine : line.length();
			line="";
			int valsOnLine = 0;
			for (Float val : valsToDisp.keySet()) {
				ArrayList<String> valNameList = valsToDisp.get(val);
				for(String valName : valNameList) {
					line += ""+valName+":" + String.format("%03f", val);
					if(valsOnLine < valsPerLine-1) {				line += " | ";			}				
					++valsOnLine;
					if (valsOnLine >= valsPerLine) {
						longestLine = longestLine >= line.length() ? longestLine : line.length();
						_mseLblDat.add(line);
						line="";
						valsOnLine = 0;
					}
				}
			}
			if(valsOnLine>0) {//catch last values
				longestLine = longestLine >= line.length() ? longestLine : line.length();
				_mseLblDat.add(line);
			}
		}	
		return longestLine;		
	}//buildDispArrayList

	
	//final setup for mouse label and label dimensions
	private final void finalizeMseLblDatCtor(ArrayList<String> _mseLblDat, int longestLine) {
		mseLabelAra = _mseLblDat.toArray(new String[1]);
		mseLabelDims = new float[] {10, -10.0f,longestLine*6.0f+10, mseLabelAra.length*10.0f + 15.0f};	
		display = true;
	}//finalizeMseLblDatCtor	

	public final void initMseDatFtrs(myPointf ptrLoc, TreeMap<Integer, Float> _ftrs, float _thresh) {
		initAllCtor(_thresh);
		//decreasing order
		TreeMap<Float, ArrayList<String>> strongestFtrs = new TreeMap<Float, ArrayList<String>>(Collections.reverseOrder());
		for(Integer ftrIDX : _ftrs.keySet()) {
			Float ftr = _ftrs.get(ftrIDX);
			if(ftr >= dispThesh) {		buildPerFtrData(ftrIDX,ftr,strongestFtrs);	}
		}	
		int count = 0;
		for(ArrayList<String> list : strongestFtrs.values()) {	count+=list.size();}
		ArrayList<String> _mseLblDat = new ArrayList<String>();

		String dispLine = getFtrDispTitleString(count);		
		int longestLine = buildDispArrayList(_mseLblDat, strongestFtrs, dispLine, 3);
		
		finalizeMseLblDatCtor(_mseLblDat, longestLine);
	}//ctor	for ftrs
	protected abstract String getFtrDispTitleString(int count);
	/**
	 * construct per feature display value
	 * @param ftrIDX : the index in the feature vector
	 * @param ftr : the value in the ftr vector
	 * @param strongestFtrs : the map being populated with the string arrays at each ftr value
	 */
	protected abstract void buildPerFtrData(Integer ftrIDX, Float ftr, TreeMap<Float, ArrayList<String>> strongestFtrs);
	
	//need to support all ftr types from map - this is built by distance/UMatrix map
	public final void initMseDatUMat(myPointf ptrLoc, float distData, float _thresh) {
		initAllCtor(_thresh);

		ArrayList<String> _mseLblDat = new ArrayList<String>();
		String line = "Dist : " + String.format("%05f", distData);
		int longestLine = line.length();
		_mseLblDat.add(line);
		
		finalizeMseLblDatCtor(_mseLblDat, longestLine);
	}//ctor for distance
	
	
	/**
	 * display nearest map node probabilities, either jp(class)-based or jpgroup(category)-based
	 * @param _map
	 * @param ptrLoc
	 * @param nearestMapNode
	 * @param _thresh
	 * @param useJPProbs
	 */
	public final void initMseDatProb(myPointf ptrLoc,  SOMMapNode nearestMapNode, float _thresh, boolean useClassProbs) {
		initAllCtor(_thresh);
		ArrayList<String> _mseLblDat = new ArrayList<String>();
		TreeMap<Float, ArrayList<String>> mapNodeProbs = new TreeMap<Float, ArrayList<String>>(Collections.reverseOrder());
		String dispLine;
		if(useClassProbs) {
			TreeMap<Integer, Float> perClassProbs = nearestMapNode.getClass_SegDataRatio();
			Float ttlNumClasses = nearestMapNode.getTtlNumMappedClassInstances();
			for(Integer cls : perClassProbs.keySet()) {
				float prob = perClassProbs.get(cls);
				ArrayList<String> valsAtProb = mapNodeProbs.get(prob);
				if(null==valsAtProb) {valsAtProb = new ArrayList<String>();}
				valsAtProb.add(""+cls);
				mapNodeProbs.put(prob, valsAtProb);				
			}	
			
			dispLine = getClassProbTitleString(nearestMapNode, Math.round(ttlNumClasses));
		} else {
			TreeMap<Integer, Float> perJPGProbs =  nearestMapNode.getCategory_SegDataRatio();
			Float ttlNumCategories = nearestMapNode.getTtlNumMappedCategoryInstances();
			for(Integer jpg : perJPGProbs.keySet()) {		
				float prob = perJPGProbs.get(jpg);
				ArrayList<String> valsAtProb = mapNodeProbs.get(prob);
				if(null==valsAtProb) {valsAtProb = new ArrayList<String>();}
				valsAtProb.add(""+jpg);
				mapNodeProbs.put(prob, valsAtProb);				
			}	
			dispLine = getCategoryProbTitleString(nearestMapNode, Math.round(ttlNumCategories));
		}
		int count = 0;
		for(ArrayList<String> list : mapNodeProbs.values()) {	count+=list.size();}
		dispLine += " count : "+ count;		
		int longestLine = buildDispArrayList(_mseLblDat, mapNodeProbs, dispLine, 3);		
		finalizeMseLblDatCtor(_mseLblDat, longestLine);
	}//ctor for nearest map node probs
	
	public void initMseDatProb(myPointf ptrLoc,  SOMMapNode nearestMapNode, float _thresh, ExDataType _type) {
		initAllCtor(_thresh);
		ArrayList<String> _mseLblDat = new ArrayList<String>();		
		int _typeIDX = _type.getVal();
		String dispLine = "# of mapped " + _type.getName() +" examples : " + nearestMapNode.getNumExamples(_typeIDX);
		int longestLine = dispLine.length();
		_mseLblDat.add(dispLine);		
		dispLine = "Has Mapped Examples : " + nearestMapNode.getHasMappedExamples(_typeIDX);
		_mseLblDat.add(dispLine);
		longestLine = longestLine >= dispLine.length() ? longestLine : dispLine.length();
		finalizeMseLblDatCtor(_mseLblDat, longestLine);
	}//ctor for nearest map nod population of mapped training examples
	
	public final void clearMseDat() {
		initAllCtor(0.0f);
		display = false;
	}
		

	protected abstract String getClassProbTitleString(SOMMapNode nearestMapNode, int ttlNumClasses);
	protected abstract String getCategoryProbTitleString(SOMMapNode nearestMapNode, int ttlNumCategories);


	//get mse-ovr label array
	public String[] getMseOvrLblArray() {	return mseLabelAra;}
	//mse label is able to be displayed
	public boolean canDisplayMseLabel() {	return display;}	
//	@Override
//	public void buildMseLbl_Ftrs() {	}
//	@Override
//	public void buildMseLbl_Dists() {	}
	/**
	 * return height of mouse label
	 * @return
	 */
	public float getMseLabelYOffset() {return display ? mseLabelDims[3] : 0.0f;}
	/**
	 * draw current mouse label data at current position
	 * @param p
	 */
	public void drawMseLbl_Info(my_procApplet p, myPointf drawLoc) {
		if(!display) {return;}
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label	
		//p.showBox(mapLoc, rad, 5, clrVal,clrVal, my_procApplet.gui_LightGreen, mseLabelDat);
		//(myPointf P, float rad, int det, int[] clrs, String[] txtAra, float[] rectDims)
		p.showBox(drawLoc, 5, 5, nodeClrs, mseLabelAra, mseLabelDims);
		p.popStyle();p.popMatrix();		
	}
	//specified by interface
	public void drawMeLblMap(my_procApplet p){drawMseLbl_Info(p,mapLoc);}
	public void setMapLoc(myPointf _pt) {mapLoc.set(_pt);}
	
}//SOM_MseOvrDisplay
