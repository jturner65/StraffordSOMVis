package strafford_SOM_PKG.straff_Utils;

import java.util.ArrayList;
import java.util.TreeMap;

import base_SOM_Objects.som_examples.enums.SOM_ExDataType;
import base_SOM_Objects.som_mapnodes.base.SOM_MapNode;
import base_SOM_Objects.som_ui.SOM_MseOvrDisplay;
import strafford_SOM_PKG.straff_Features.Straff_MonitorJpJpgrp;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;


//this class is for a simple object to just represent a mouse-over on the visualization of the map
public class Straff_SOMMseOvrDisp extends SOM_MseOvrDisplay{
	private Straff_MonitorJpJpgrp jpJpgMon;
		
	public Straff_SOMMseOvrDisp(Straff_SOMMapManager _map, float _thresh) {
		super(_map, _thresh);	
	}//Straff_SOMDispMapExample
	
	@Override
	protected int[] setNodeColors() {return mapMgr.getClrFillStrkTxtAra(SOM_ExDataType.MouseOver);}
	@Override
	protected String getFtrDispTitleString(int count) { return "JPs :  count : "+count;}
	
	/**
	 * construct per feature display value
	 * @param ftrIDX : the index in the feature vector
	 * @param ftr : the value in the ftr vector
	 * @param strongestFtrs : the map being populated with the string arrays at each ftr value
	 */
	@Override
	protected void buildPerFtrData(Integer ftrIDX, Float ftr, TreeMap<Float, ArrayList<String>> strongestFtrs) {
		Integer jp = jpJpgMon.getFtrJpByIdx(ftrIDX);
		ArrayList<String> vals = strongestFtrs.get(ftr);
		if(null==vals) {vals = new ArrayList<String>();}
		vals.add(""+jp);
		strongestFtrs.put(ftr, vals);
	}//buildPerFtrData

	@Override
	protected String getClassProbTitleString(SOM_MapNode nearestMapNode, int ttlNumClasses) { return nearestMapNode.mapNodeCoord.toString() + " Order JP Probs : ("+ttlNumClasses+" orders mapped) Ttl JP ";}
	@Override
	protected String getCategoryProbTitleString(SOM_MapNode nearestMapNode, int ttlNumCategories) { return nearestMapNode.mapNodeCoord.toString() + " Order JPGroup Probs : ("+ttlNumCategories+" orders mapped) Ttl JPG ";}
	
	/**
	 * instancing-specific initialization called for every data change for mouse object
	 */
	@Override
	protected void initAll_Indiv() {jpJpgMon = ((Straff_SOMMapManager)mapMgr).jpJpgrpMon;}
	/**
	 * instancing-specific finalizing called after every mouse-over data process
	 */
	@Override
	protected void finalizeMseLblDatCtor_Indiv(ArrayList<String> _mseLblDat, int longestLine) {	}
	/**
	 * instancing-specific clearing called when mouse over display is cleared
	 */
	@Override
	protected void clearMseDat_Indiv() {	}

}//Straff_SOMMseOvrDisp