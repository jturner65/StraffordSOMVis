package strafford_SOM_PKG.straff_SOM_Examples;

import java.util.*;

import base_SOM_Objects.som_examples.*;
import base_UI_Objects.*;
import base_Utils_Objects.*;

import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

//this class is for a simple object to just represent a mouse-over on the visualization of the map
public class DispSOMMapExample extends Straff_SOMExample implements ISOMMap_DispExample{
	private float ftrThresh;
	private int mapType;
	private int[] clrVal = new int[] {255,255,0,255};
	private String[] mseLabelAra;
	private float[] mseLabelDims;

	//need to support all ftr types from map - what type of ftrs are these? only using/displaying -training- features
	//this is called when mse-over on ftr map
	public DispSOMMapExample(Straff_SOMMapManager _map, myPointf ptrLoc, TreeMap<Integer, Float> _ftrs, float _thresh) {
		super(_map, ExDataType.MouseOver,"Mse_"+ptrLoc.toStrBrf());//(" + String.format("%.4f",this.x) + ", " + String.format("%.4f",this.y) + ", " + String.format("%.4f",this.z)+")
		//type of features used for currently trained map
		mapType = mapMgr.getCurrentTrainDataFormat();
		
		ftrMaps[ftrMapTypeKey] = new TreeMap<Integer, Float>();	
		ftrThresh = _thresh;
		allProdJPs = new HashSet<Integer>();
		allNonZeroFtrIDXs = new ArrayList<Integer>();
		//decreasing order
		TreeMap<Float, String> strongestFtrs = new TreeMap<Float, String>(Collections.reverseOrder());
		for(Integer ftrIDX : _ftrs.keySet()) {
			Float ftr = _ftrs.get(ftrIDX);
			if(ftr >= ftrThresh) {	
				Integer jp = jpJpgMon.getFtrJpByIdx(ftrIDX);
				allProdJPs.add(jp);
				allNonZeroFtrIDXs.add(ftrIDX);	
				ftrMaps[ftrMapTypeKey].put(ftrIDX, ftr);
				strongestFtrs.put(ftr, ""+jp);
			}
		}	
		ArrayList<String> _mseLblDat = new ArrayList<String>();
		int longestLine = 4;
		String line = "JPs : ";
		//descriptive mouse-over label - top x jp's
		if (allProdJPs.size()== 0) {	_mseLblDat.add(line + "None");	}
		else {
			int jpOnLine = 0, jpPerLine = 3;
			for (Float ftr : strongestFtrs.keySet()) {
				String jpName = strongestFtrs.get(ftr);
				line += ""+jpName+":" + String.format("%03f", ftr);
				if(jpOnLine < jpPerLine-1) {				line += " | ";			}
				longestLine = longestLine >= line.length() ? longestLine : line.length();
				++jpOnLine;
				if (jpOnLine >= jpPerLine) {
					_mseLblDat.add(line);
					line="";
					jpOnLine = 0;
				}
			}
		}	
		mseLabelAra = _mseLblDat.toArray(new String[1]);
		mseLabelDims = new float[] {10, -10.0f,longestLine*6.0f, mseLabelAra.length*10.0f + 15.0f};
	}//ctor	
	//need to support all ftr types from map - this is built by distance/UMatrix map
	public DispSOMMapExample(Straff_SOMMapManager _map, myPointf ptrLoc, float distData, float _thresh) {
		super(_map, ExDataType.MouseOver,"Mse_"+ptrLoc.toStrBrf());//(" + String.format("%.4f",this.x) + ", " + String.format("%.4f",this.y) + ", " + String.format("%.4f",this.z)+")
		//type of features used for currently trained map
		mapType = mapMgr.getCurrentTrainDataFormat();
		
		ftrMaps[ftrMapTypeKey] = new TreeMap<Integer, Float>();	
		ftrThresh = _thresh;
		allProdJPs = new HashSet<Integer>();
		allNonZeroFtrIDXs = new ArrayList<Integer>();

		ArrayList<String> _mseLblDat = new ArrayList<String>();
		String line = "Dist : " + String.format("%05f", distData);
		int longestLine = line.length();
		_mseLblDat.add(line);

		mseLabelAra = _mseLblDat.toArray(new String[1]);
		mseLabelDims = new float[] {10, -10.0f,longestLine*6.0f+10, mseLabelAra.length*10.0f + 15.0f};
	}//ctor		
	
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
		buildCompFtrVector(0.0f);
		setFlag(stdFtrsBuiltIDX,true);
	}


}//DispSOMMapExample