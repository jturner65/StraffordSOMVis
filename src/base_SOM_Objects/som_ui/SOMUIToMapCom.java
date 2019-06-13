package base_SOM_Objects.som_ui;

import java.util.HashMap;

import base_SOM_Objects.SOM_MapManager;

/**
 * an object of this class provides an interface to sync the current map values
 * with a UI tracking these values and allowing for modification.
 * adherence to ISOM_UIWinMapDat should guarantee communication compatibility with this object
 * 
 * @author john
 *
 */
public class SOMUIToMapCom {
	//owning map manager
	protected SOM_MapManager mapMgr;	
	//ref to owning window
	protected ISOM_UIWinMapDat win;

	public SOMUIToMapCom(SOM_MapManager _mapMgr, ISOM_UIWinMapDat _win) {	
		mapMgr = _mapMgr;win=_win;
	}	
	////////////////////////////////////
	// update UI window with new values
	
	//make sure UI updates do not trigger update events below - endless loop
	public void updateUIFromMapDat_Integer(String key, Integer val) {if(win==null) {return;}win.updateUIDataVal_Integer(key,val);}//updateUIFromMapDat_Integer
	public void updateUIFromMapDat_Float(String key, Float val) {	if(win==null) {return;}	win.updateUIDataVal_Float(key,val);}//updateUIFromMapDat_Float
	public void updateUIFromMapDat_String(String key, String val) {	if(win==null) {return;}	win.updateUIDataVal_String(key,val);}//updateUIFromMapDat_String
	
	////////////////////////////////////
	// update map data object with values from UI input changes
	public void updateMapDatFromUI_Integer(String key, Integer val) {	mapMgr.updateMapDatFromUI_Integer(key, val);}	
	public void updateMapDatFromUI_Float(String key, Float val) {		mapMgr.updateMapDatFromUI_Float(key, val);}
	public void updateMapDatFromUI_String(String key, String val) {		mapMgr.updateMapDatFromUI_String(key, val);}
	
	public void updateAllMapArgsFromUI(HashMap<String, Integer> mapInts, HashMap<String, Float> mapFloats, HashMap<String, String> mapStrings) {mapMgr.updateAllMapArgsFromUI(mapInts, mapFloats,mapStrings);}

}//SOMMapUIInterface
