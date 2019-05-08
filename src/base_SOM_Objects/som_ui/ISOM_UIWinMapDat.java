package base_SOM_Objects.som_ui;


/**
 * these are the communication protocols required between whatever UI window is implemented 
 * and the underlying system. These guarantee appropriate UI communication
 * @author john
 */
public interface ISOM_UIWinMapDat {
	//arrays holding mapDat names and ui obj indexes of som map ui values
	final String[] mapDatNames = new String[] {
		"mapCols","mapRows","mapEpochs","mapKType","mapStRad","mapEndRad","mapStLrnRate","mapEndLrnRate",
		"mapGridShape","mapBounds","mapRadCool","mapNHood", "mapLearnCool"			
	};

	final String[] mapDatNames_Ints = new String[] {"mapCols","mapRows","mapEpochs","mapKType","mapStRad","mapEndRad"};
	final String[] mapDatNames_Floats = new String[] {"mapStLrnRate","mapEndLrnRate"};
	final String[] mapDatNames_Strings = new String[] {"mapGridShape","mapBounds","mapRadCool","mapNHood", "mapLearnCool"};	
	
	/**
	 * this method will determine if the passed map data key exists in the passed map data name string
	 * @param names map data names list of a particular type
	 * @param key 
	 * @return
	 */
	default boolean isMapNameOfType(String[] names, String key) {
		for(String n : names) {if(key.equals(n)) {return true;}}
		return false;		
	}
	
	/**
	 * Given a UI object's IDX value, provide the string MapDat key corresponding to it
	 * @param UIidx : UI object idx
	 * @return : string MapName corresponding to this object Idx
	 */
	String getMapKeyStringFromUIidx(int UIidx);

	/**
	 * Given MapDat key, return an int corresponding to the appropriate ui object in the instancing window
	 * @param mapKey : string map name corresponding to a map dat variable
	 * @return in ui object idx
	 */
	int getUIidxFromMapKeyString(String mapKey);

	/**
	 * send UI values for a particular UI object index to appropriate. This function is responsible for converting val to an integer
	 * @param UIidx : idx of gui object - will be mapped to string name from mapDatNames above
	 * @param val : value from UI 
	 */	
	void setMapDataVal_Integer(int UIidx, double val);	
	/**
	 * send UI values for a particular UI object index to appropriate. This function is responsible for converting val to a float
	 * @param UIidx : idx of gui object - will be mapped to string name from mapDatNames above
	 * @param val : value from UI 
	 */	
	void setMapDataVal_Float(int UIidx, double val);
	/**
	 * send UI values for a particular UI object index to appropriate. This function is intended to be used by a dropdown list and 
	 * is responsible for converting val to the appropriate string (by list lookup for example)
	 * @param UIidx : idx of gui object - will be mapped to string name from mapDatNames above
	 * @param val : value from UI : integer index of a list
	 */		
	void setMapDataVal_String(int UIidx, double val);
	
	/**
	 * Set UI values to reflect current map state. This function is responsible for mapping string descriptor from mapDatNames above
	 * to appropriate UI object index
	 * @param key : mapDatNames descriptor of object - must be mapped to UI object by instancing class
	 * @param val : value to set 
	 */
	void updateUIDataVal_Integer(String key, Integer val);
	
	/**
	 * Set UI values to reflect current map state. This function is responsible for mapping string descriptor from mapDatNames above
	 * to appropriate UI object index
	 * @param key : mapDatNames descriptor of object - must be mapped to UI object by instancing class
	 * @param val : value to set 
	 */
	void updateUIDataVal_Float(String key, Float val);
	
	/**
	 * Set UI values to reflect current map state. This function is responsible for mapping string descriptor from mapDatNames above
	 * to appropriate UI object index
	 * @param key : mapDatNames descriptor of object - must be mapped to UI object by instancing class
	 * @param val : value to set : string value corresponding to ddl entry
	 */
	void updateUIDataVal_String(String key, String val);
	

}
