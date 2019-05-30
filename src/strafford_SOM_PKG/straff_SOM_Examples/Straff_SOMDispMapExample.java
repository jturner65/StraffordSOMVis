package strafford_SOM_PKG.straff_SOM_Examples;

import java.util.*;

import base_SOM_Objects.som_examples.*;
import base_UI_Objects.*;
import base_Utils_Objects.*;

import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

//this class is for a simple object to just represent a mouse-over on the visualization of the map
public class Straff_SOMDispMapExample extends Straff_SOMExample implements ISOM_DispMapExample{
	private float ftrThresh;
	private int mapType;
	private int[] clrVal = new int[] {255,255,0,255};
	private String[] mseLabelAra;
	private float[] mseLabelDims;

	//need to support all ftr types from map - what type of ftrs are these? only using/displaying -training- features
	//this is called when mse-over on ftr map
	public Straff_SOMDispMapExample(Straff_SOMMapManager _map, myPointf ptrLoc, TreeMap<Integer, Float> _ftrs, float _thresh) {
		super(_map, ExDataType.MouseOver,"Mse_"+ptrLoc.toStrBrf());//(" + String.format("%.4f",this.x) + ", " + String.format("%.4f",this.y) + ", " + String.format("%.4f",this.z)+")
		initAllCtor(_thresh);
		//decreasing order
		TreeMap<Float, ArrayList<String>> strongestFtrs = new TreeMap<Float, ArrayList<String>>(Collections.reverseOrder());
		for(Integer ftrIDX : _ftrs.keySet()) {
			Float ftr = _ftrs.get(ftrIDX);
			if(ftr >= ftrThresh) {	
				Integer jp = jpJpgMon.getFtrJpByIdx(ftrIDX);
				allProdJPs.add(jp);
				allProdJPGroups.add(jpJpgMon.getFtrJpGroupFromJp(jp));
				allNonZeroFtrIDXs.add(ftrIDX);	
				ftrMaps[ftrMapTypeKey].put(ftrIDX, ftr);
				ArrayList<String> vals = strongestFtrs.get(ftr);
				if(null==vals) {vals = new ArrayList<String>();}
				vals.add(""+jp);
				strongestFtrs.put(ftr, vals);
			}
		}	
		int count = 0;
		for(ArrayList<String> list : strongestFtrs.values()) {	count+=list.size();}
		ArrayList<String> _mseLblDat = new ArrayList<String>();

		String dispLine = "JPs :  count : "+count;		
		int longestLine = buildDispArrayList(_mseLblDat, strongestFtrs, dispLine, 3);
		
		finalizeMseLblDatCtor(_mseLblDat, longestLine);
	}//ctor	for ftrs
	
	//need to support all ftr types from map - this is built by distance/UMatrix map
	public Straff_SOMDispMapExample(Straff_SOMMapManager _map, myPointf ptrLoc, float distData, float _thresh) {
		super(_map, ExDataType.MouseOver,"Mse_"+ptrLoc.toStrBrf());//(" + String.format("%.4f",this.x) + ", " + String.format("%.4f",this.y) + ", " + String.format("%.4f",this.z)+")
		initAllCtor(_thresh);

		ArrayList<String> _mseLblDat = new ArrayList<String>();
		String line = "Dist : " + String.format("%05f", distData);
		int longestLine = line.length();
		_mseLblDat.add(line);
		
		finalizeMseLblDatCtor(_mseLblDat, longestLine);
	}//ctor for distance

	/**
	 * display nearest map node probabilities, either jp-based or jpgroup-based
	 * @param _map
	 * @param ptrLoc
	 * @param nearestMapNode
	 * @param _thresh
	 * @param useJPProbs
	 */
	public Straff_SOMDispMapExample(Straff_SOMMapManager _map, myPointf ptrLoc, SOMMapNode nearestMapNode, float _thresh, boolean useJPProbs) {
		super(_map, ExDataType.MouseOver,"Mse_"+ptrLoc.toStrBrf());//(" + String.format("%.4f",this.x) + ", " +
		initAllCtor(_thresh);
		ArrayList<String> _mseLblDat = new ArrayList<String>();
		TreeMap<Float, ArrayList<String>> mapNodeProbs = new TreeMap<Float, ArrayList<String>>(Collections.reverseOrder());
		String dispLine;
		if(useJPProbs) {
			TreeMap<Integer, Float> perJPProbs = nearestMapNode.getClass_SegDataRatio();
			Float ttlNumClasses = nearestMapNode.getTtlNumMappedClassInstances();
			for(Integer jp : perJPProbs.keySet()) {
				float prob = perJPProbs.get(jp);
				ArrayList<String> valsAtProb = mapNodeProbs.get(prob);
				if(null==valsAtProb) {valsAtProb = new ArrayList<String>();}
				valsAtProb.add(""+jp);
				mapNodeProbs.put(prob, valsAtProb);				
			}	
			
			dispLine = nearestMapNode.mapNodeCoord.toString() + " Order JP Probs : ("+ttlNumClasses+" order mapped) ";
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
			dispLine = nearestMapNode.mapNodeCoord.toString() + " Order JPGroup Probs : ("+ttlNumCategories+" order mapped) ";
		}
		int count = 0;
		for(ArrayList<String> list : mapNodeProbs.values()) {	count+=list.size();}
		dispLine += " count : "+ count;		
		int longestLine = buildDispArrayList(_mseLblDat, mapNodeProbs, dispLine, 3);		
		finalizeMseLblDatCtor(_mseLblDat, longestLine);
	}//ctor for nearest map node probs
	
	public Straff_SOMDispMapExample(Straff_SOMMapManager _map, myPointf ptrLoc, SOMMapNode nearestMapNode, float _thresh, ExDataType _type) {
		super(_map, ExDataType.MouseOver,"Mse_"+ptrLoc.toStrBrf());
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
	
	
	private void initAllCtor(float _thresh) {
		//type of features used for currently trained map
		mapType = mapMgr.getCurrentTrainDataFormat();		
		ftrMaps[ftrMapTypeKey] = new TreeMap<Integer, Float>();	
		ftrThresh = _thresh;
		allNonZeroFtrIDXs = new ArrayList<Integer>();		
	}//initAllCtor
	
	//final setup for mouse label and label dimensions
	private void finalizeMseLblDatCtor(ArrayList<String> _mseLblDat, int longestLine) {
		mseLabelAra = _mseLblDat.toArray(new String[1]);
		mseLabelDims = new float[] {10, -10.0f,longestLine*6.0f+10, mseLabelAra.length*10.0f + 15.0f};		
	}//finalizeMseLblDatCtor	
	
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
	
	@Override
	public void buildMseLbl_Ftrs() {	}
	@Override
	public void buildMseLbl_Dists() {	}

	//not used by this object
	@Override
	protected HashSet<Tuple<Integer, Integer>> getSetOfAllJpgJpData() {	return null;}//getSetOfAllJpgJpData
	@Override
	protected void buildFeaturesMap() { }	
	@Override
	public String getRawDescrForCSV() {	return "Should not save DispSOMMapExample to CSV";}
	@Override
	public String getRawDescColNamesForCSV() {return "Do not save DispSOMMapExample to CSV";}
	@Override
	protected void setIsTrainingDataIDX_Priv() {}
	@Override
	public void finalizeBuildBeforeFtrCalc() {}	
	@Override
	//called after all features of this kind of object are built
	public void postFtrVecBuild() {}
	//this will return the training label(s) of this example - a mouse-over node is never used as training data
	//they should not be used for supervision during/after training
	@Override
	public TreeMap<Integer,Integer> getTrainingLabels() {return null;}

	/**
	 *  this will build the comparison feature vector array that is used as the comparison vector 
	 *  in distance measurements - for most cases this will just be a copy of the ftr vector array
	 *  but in some instances, there might be an alternate vector to be used to handle when, for 
	 *  example, an example has ftrs that do not appear on the map
	 * @param _ignored : ignored
	 */
	public final void buildCompFtrVector(float _ignored) {	compFtrMaps = ftrMaps;}

	@Override
	//specified by interface
	public void drawMeLblMap(my_procApplet p){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label	
		//p.showBox(mapLoc, rad, 5, clrVal,clrVal, my_procApplet.gui_LightGreen, mseLabelDat);
		//(myPointf P, float rad, int det, int[] clrs, String[] txtAra, float[] rectDims)
		p.showBox(mapLoc, 5, 5,nodeClrs, mseLabelAra, mseLabelDims);
		p.popStyle();p.popMatrix();		
	}//drawLabel	

	@Override
	protected void buildStdFtrsMap() {			
		if (allNonZeroFtrIDXs.size() > 0) {calcStdFtrVector(ftrMaps[ftrMapTypeKey], ftrMaps[stdFtrMapTypeKey], mapMgr.getTrainFtrMins(), mapMgr.getTrainFtrDiffs());}
		else {ftrMaps[stdFtrMapTypeKey] = new TreeMap<Integer, Float>();}
		setFlag(stdFtrsBuiltIDX,true);
	}

}//DispSOMMapExample