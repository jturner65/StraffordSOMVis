package base_SOM_Objects.som_ui;


import java.io.File;
import java.util.*;
import java.util.concurrent.Future;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_UI_Objects.*;
import base_UI_Objects.drawnObjs.myDrawnSmplTraj;
import base_UI_Objects.windowUI.myDispWindow;
import base_UI_Objects.windowUI.myGUIObj;
import base_Utils_Objects.*;
import base_Utils_Objects.io.MessageObject;
import base_Utils_Objects.io.MsgCodes;
import base_Utils_Objects.vectorObjs.Tuple;
import base_Utils_Objects.vectorObjs.myPoint;
import base_Utils_Objects.vectorObjs.myPointf;
import processing.core.PImage;

/**
 * base UI window functionality to be used for any SOM-based projects
 * @author john
 *
 */
public abstract class SOMMapUIWin extends myDispWindow implements ISOM_UIWinMapDat{
	//map manager that is instanced 
	public SOM_MapManager mapMgr;
	//interface to facilitate keeping UI and the SOM MapData object synched w/respect to map data values
	public SOMUIToMapCom mapUIAPI;
	//msgObj responsible for displaying messages to console and printing to log file
	protected MessageObject msgObj;
	
	//idxs of boolean values/flags
	public static final int 
		buildSOMExe 				= 0,			//command to initiate SOM-building
		resetMapDefsIDX				= 1,			//reset default UI values for map
		mapDataLoadedIDX			= 2,			//whether map has been loaded or not	
		mapUseChiSqDistIDX			= 3,			//whether to use chi-squared (weighted by variance) distance for features or regular euclidean dist
		mapExclProdZeroFtrIDX		= 4,			//whether or not distances between two datapoints assume that absent features in source data point should be zero or ignored when comparing to map node ftrs

		//display/interaction
		mapDrawTrainDatIDX			= 5,			//draw training examples
		mapDrawTestDatIDX 			= 6,			//draw testing examples - data held out and not used to train the map 
		mapDrawNodeLblIDX			= 7,			//draw labels for nodes
		mapDrawWtMapNodesIDX		= 8,			//draw map nodes with non-0 (present) wt vals
		mapDrawPopMapNodesIDX	   	= 9,			//draw map nodes that are bmus for training examples
		mapDrawAllMapNodesIDX		= 10,			//draw all map nodes, even empty
		//UMatrix 		
		mapDrawUMatrixIDX			= 11,			//draw visualization of u matrix - distance between nodes
		mapDrawUMatSegImgIDX		= 12,			//draw the image of the interpolated segments based on UMatrix Distance
		mapDrawUMatSegMembersIDX	= 13,			//draw umatrix-based segments around regions of maps - visualizes clusters with different colors
		//ftr and ftr-dist-based
		mapDrawDistImageIDX			= 14,			//draw umatrix-like rendering based on sq dist between adjacent node vectors
		mapDrawFtrWtSegMembersIDX	= 15,			//draw ftr-wt-based segments around regions of map - display only segment built from currently display ftr on ftr map
		//class and category-based segments
		mapDrawClassSegmentsIDX		= 16,			//show class (order-based jp) segments
		mapDrawCategorySegmentsIDX	= 17,			//show category (order-driven jpgroup) segments
		
		showSelRegionIDX			= 18,			//highlight a specific region of the map, either all nodes above a certain threshold for a chosen ftr
		//train/test data managemen
		somTrainDataLoadedIDX		= 19,			//whether data used to build map has been loaded yet
		saveLocClrImgIDX			= 20,			//
		//save segment mappings
		saveAllSegmentMapsIDX		= 21;			//this will save all the segment mappings that have been defined
	
	public static final int numSOMBasePrivFlags = 22;
	//instancing class will determine numPrivFlags based on how many more flags are added
	
	//SOM map list options
	public String[] 
		uiMapShapeList = new String[] {"rectangular","hexagonal"},
		uiMapBndsList = new String[] {"planar","toroid"},
		uiMapKTypList = new String[] {"Dense CPU", "Dense GPU", "Sparse CPU"},
		uiMapNHoodList = new String[] {"gaussian","bubble"},
		uiMapRadClList = new String[] {"linear","exponential"},
		uiMapLrnClList = new String[] {"linear","exponential"},
		uiMapDrawExToBmuTypeList = SOM_MapManager.getNodeBMUMapTypes(),
		uiMapTestFtrTypeList = SOM_MapManager.uiMapTrainFtrTypeList,//new String[] {"Unmodified","Standardized (0->1 per ftr)","Normalized (vector mag==1)"};
		uiMapTrainFtrTypeList = SOM_MapManager.uiMapTrainFtrTypeList;//new String[] {"Unmodified","Standardized (0->1 per ftr)","Normalized (vector mag==1)"};

	
	//	//GUI Objects	
	public final static int 
		uiTrainDataFrmtIDX			= 0,			//format that training data should take : unmodified, normalized or standardized
		uiTestDataFrmtIDX			= 1,			//format of vectors to use when comparing examples to nodes on map
		uiTrainDatPartIDX			= 2,			//partition % of training data out of total data (rest is testing)
		
		uiMapRowsIDX 				= 3,            //map rows
		uiMapColsIDX				= 4,			//map cols
		uiMapEpochsIDX				= 5,			//# of training epochs
		uiMapShapeIDX				= 6,			//hexagonal or rectangular
		uiMapBndsIDX				= 7,			//planar or torroidal bounds
		uiMapKTypIDX				= 8,			//0 : dense cpu, 1 : dense gpu, 2 : sparse cpu.  dense needs appropriate lrn file format
		uiMapNHdFuncIDX				= 9,			//neighborhood : 0 : gaussian, 1 : bubble
		uiMapRadCoolIDX				= 10,			//radius cooling 0 : linear, 1 : exponential
		uiMapLrnCoolIDX				= 11,			//learning rate cooling 0 : linear 1 : exponential
		uiMapLrnStIDX				= 12,			//start learning rate
		uiMapLrnEndIDX				= 13,			//end learning rate
		uiMapRadStIDX				= 14,			//start radius
		uiMapRadEndIDX				= 15,			//end radius
		
		uiMapNodeBMUTypeToDispIDX 	= 16,			//type of examples mapping to a particular node to display in visualization
		uiNodeWtDispThreshIDX 		= 17,			//threshold for display of map nodes on individual weight maps
		uiNodeInSegThreshIDX		= 18,			//threshold of u-matrix weight for nodes to belong to same segment
		uiMseRegionSensIDX			= 19;			//senstivity threshold for mouse-over, to determine membership to a particular jp (amount a query on the map per feature needs to be to be considered part of the JP that feature represents)	
	
	public static final int numSOMBaseGUIObjs = 20;
	//instancing class will specify numGUIObjs
	
	protected double[] uiVals;				//raw values from ui components
	//
	//match descriptor string to index and index to string, to facilitate access
	public TreeMap<String, Integer> mapDatDescrToUIIdx;
	public TreeMap<Integer,String> mapUIIdxToMapDatDescr;
	//array of gui object idxs corresponding positionally with map dat names specified above
	public static final int[] mapObjUIIdxs = new int[] {
		uiMapColsIDX, uiMapRowsIDX, uiMapEpochsIDX, uiMapKTypIDX,uiMapRadStIDX, uiMapRadEndIDX,uiMapLrnStIDX,uiMapLrnEndIDX,
		uiMapShapeIDX ,uiMapBndsIDX,uiMapRadCoolIDX,uiMapNHdFuncIDX, uiMapLrnCoolIDX
	};	
	
	//threshold of wt value to display map node
	protected float mapNodeWtDispThresh;
	//type of examples using each map node as a bmu to display
	protected ExDataType mapNodeDispType;
	
	//////////////////////////////
	//map drawing 	draw/interaction variables
	public int[] dpFillClr, dpStkClr;
	
	//start location of SOM image - stX, stY, and dimensions of SOM image - width, height; locations to put calc analysis visualizations
	public float[] SOM_mapLoc, SOM_mapDims;
	
	//array of per-ftr map wts
	protected PImage[] mapPerFtrWtImgs;
	//image of umatrix (distance between nodes)
	protected PImage mapCubicUMatrixImg;
	//image of segments suggested by UMat Dist
	protected PImage mapUMatrixCubicSegmentsImg;
	
	//which ftr map is currently being shown
	protected int curMapImgIDX;

	//scaling value - use this to decrease the image size and increase the scaling so it is rendered the same size
	protected static final float mapScaleVal = 10.0f;
	
	protected SOM_MseOvrDisplay mseOvrData;//location and label of mouse-over point in map
	
	
	public SOMMapUIWin(my_procApplet _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed,	String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt, _canDrawTraj);
		initAndSetSOMDatUIMaps();
	}//ctor
	
	//set convenience maps to more easily access UI objects related to map data/args
	private void initAndSetSOMDatUIMaps() {
		//build map descriptor index to mapdat descriptor string map. 
		mapDatDescrToUIIdx = new TreeMap<String, Integer>();
		mapUIIdxToMapDatDescr = new TreeMap<Integer,String> ();
		//mapDatNames mapObjUIIdxs
		for(int i=0;i<mapDatNames.length;++i) {
			String mapDatName = mapDatNames[i];
			Integer uiObjIDX = mapObjUIIdxs[i];
			mapDatDescrToUIIdx.put(mapDatName, uiObjIDX);
			mapUIIdxToMapDatDescr.put(uiObjIDX,mapDatName);
		}
	}//initAndSetSOMDatUIMaps
	
	/**
	 * Given a UI object's IDX value, provide the string MapDat key corresponding to it
	 * @param UIidx
	 * @return
	 */
	@Override
	public String getMapKeyStringFromUIidx(int UIidx) {		return mapUIIdxToMapDatDescr.get(UIidx);	}

	/**
	 * Given MapDat key, return an int corresponding to the appropriate ui object in the instancing window
	 * @param UIidx
	 * @return
	 */
	@Override
	public int getUIidxFromMapKeyString(String mapKey){		return mapDatDescrToUIIdx.get(mapKey);	}


	@Override
	protected final void initMe() {
		//initUIBox();				//set up ui click region to be in sidebar menu below menu's entries	
		//start x and y and dimensions of full map visualization as function of visible window size;
		float width = rectDim[3]-(2*xOff);//actually also height, but want it square, and space is wider than high, so we use height as constraint - ends up being 834.8 x 834.8 with default screen dims and without side menu
		SOM_mapDims = new float[] {width,width};
		mapMgr = buildMapMgr();
		mapUIAPI = mapMgr.mapUIAPI;
		setVisScreenWidth(rectDim[2]);
		//only set for visualization - needs to reset static refs in msgObj
		mapMgr.setPADispWinData(this, pa);
		//used to display results within this window
		msgObj = mapMgr.buildMsgObj();
		
		//this window uses right side info window
		setFlags(drawRightSideMenu, true);		//may need some re-scaling to keep things in the middle and visible
		
		//init specific sim flags
		initAllPrivFlags();		//call in instancing class when # of flags is known		
		setPrivFlags(mapDrawTrainDatIDX,false);
		setPrivFlags(mapDrawWtMapNodesIDX,false);
		setPrivFlags(mapUseChiSqDistIDX,false);
		setPrivFlags(mapDrawUMatrixIDX, true);
		setPrivFlags(mapExclProdZeroFtrIDX, true);

		dpFillClr = pa.getClr(pa.gui_White);
		dpStkClr = pa.getClr(pa.gui_Blue);	
		
		mapMgr.setCurrentTrainDataFormat((int)(this.guiObjs[uiTrainDataFrmtIDX].getVal()));
		mapMgr.setCurrentTestDataFormat((int)(this.guiObjs[uiTestDataFrmtIDX].getVal()));
		mapNodeWtDispThresh = (float)(this.guiObjs[uiNodeWtDispThreshIDX].getVal());
		mapNodeDispType = ExDataType.getVal((int)(this.guiObjs[uiMapNodeBMUTypeToDispIDX].getVal()));
		mseOvrData = null;	
		initMeIndiv();
	}//initMe()	
	
	protected abstract SOM_MapManager buildMapMgr() ;
	protected abstract void initMeIndiv();	
	
	@Override
	//initialize all private-flag based UI buttons here - called by base class
	public final void initAllPrivBtns(){	
		//add an entry for each button, in the order they are wished to be displayed		
		ArrayList<Object[]> tmpBtnNamesArray = new ArrayList<Object[]>();
		tmpBtnNamesArray.add(new Object[]{"Building SOM","Build SOM ",buildSOMExe});
		tmpBtnNamesArray.add(new Object[]{"Reset Dflt UI Vals","Reset Dflt UI Vals",resetMapDefsIDX});
		tmpBtnNamesArray.add(new Object[]{"Using ChiSq for Ftr Dist", "Not Using ChiSq Distance", mapUseChiSqDistIDX});       
		tmpBtnNamesArray.add(new Object[]{"Prdct Dist ignores 0-ftrs","Prdct Dist w/all ftrs", mapExclProdZeroFtrIDX});    
		tmpBtnNamesArray.add(new Object[]{"Hide Train Data", "Show Train Data", mapDrawTrainDatIDX});       
		tmpBtnNamesArray.add(new Object[]{"Hide Test Data", "Show Test Data", mapDrawTestDatIDX});        
		tmpBtnNamesArray.add(new Object[]{"Hide Nodes", "Show Nodes", mapDrawAllMapNodesIDX});    
		tmpBtnNamesArray.add(new Object[]{"Hide Lbls", "Show Lbls", mapDrawNodeLblIDX});        
		tmpBtnNamesArray.add(new Object[]{"Hide Nodes (by Pop)", "Show Nodes (by Pop)", mapDrawPopMapNodesIDX});    
		tmpBtnNamesArray.add(new Object[]{"Showing UMat (Bi-Cubic)", "Showing Ftr Map", mapDrawUMatrixIDX});        
		tmpBtnNamesArray.add(new Object[]{"Hide Hot Ftr Nodes (by Wt)", "Show Hot Ftr Nodes (by Wt)", mapDrawWtMapNodesIDX});     
		tmpBtnNamesArray.add(new Object[]{"Hide Ftr Wt Segments", "Show Ftr Wt Segments", mapDrawFtrWtSegMembersIDX});
		tmpBtnNamesArray.add(new Object[]{"Hide Clstr (U-Dist)", "Show Clstr (U-Dist)", mapDrawUMatSegMembersIDX}); 
		tmpBtnNamesArray.add(new Object[]{"Hide Clstr Image", "Show Clstr Image", mapDrawUMatSegImgIDX});     

		String[] classBtnTFLabels = getClassBtnTFLabels();
		if((null != classBtnTFLabels) && (classBtnTFLabels.length == 2)) {tmpBtnNamesArray.add(new Object[]{classBtnTFLabels[0],classBtnTFLabels[1],mapDrawClassSegmentsIDX});}
		
		String[] catBtnTFLabels = getCategoryBtnTFLabels();
		if((null != catBtnTFLabels) && (catBtnTFLabels.length == 2)) {tmpBtnNamesArray.add(new Object[]{catBtnTFLabels[0],catBtnTFLabels[1],mapDrawCategorySegmentsIDX});}		
		
		String[] saveSegmentTFLabels = getSegmentSaveBtnTFLabels();
		if((null != classBtnTFLabels) && (classBtnTFLabels.length == 2)) {tmpBtnNamesArray.add(new Object[]{saveSegmentTFLabels[0],saveSegmentTFLabels[1],saveAllSegmentMapsIDX});}		
		
		//add instancing-class specific buttons
		initAllSOMPrivBtns_Indiv(tmpBtnNamesArray);
		//finalize setup for UI toggle buttons - convert to arrays
		truePrivFlagNames = new String[tmpBtnNamesArray.size()];
		falsePrivFlagNames = new String[truePrivFlagNames.length];
		privModFlgIdxs = new int[truePrivFlagNames.length];
		for(int i=0;i<truePrivFlagNames.length;++i) {
			Object[] tmpAra = tmpBtnNamesArray.get(i);
			truePrivFlagNames[i] = (String) tmpAra[0];
			falsePrivFlagNames[i] = (String) tmpAra[1];
			privModFlgIdxs[i] = (int) tmpAra[2];
		}		
		numClickBools = truePrivFlagNames.length;	
		initPrivBtnRects(0,numClickBools);		
		
	}//initAllPrivBtns

	/**
	 * Instancing class-specific (application driven) UI buttons to display are built 
	 * in this function.  Add an entry to tmpBtnNamesArray for each button, in the order 
	 * they are to be displayed
	 * @param tmpBtnNamesArray array list of Object arrays, where in each object array : 
	 * 			the first element is the true string label, 
	 * 			the 2nd elem is false string array, and 
	 * 			the 3rd element is integer flag idx 
	 */
	protected abstract void initAllSOMPrivBtns_Indiv(ArrayList<Object[]> tmpBtnNamesArray);
	//these are used by instancing class to determine the names of the class and category data values used.  if these are empty then that means these features are not used
	/**
	 * Instance class determines the true and false labels the class buttons use - if empty then no classes used
	 * @return array holding true(idx0) and false(idx1) labels for buttons to control display of class-based segment
	 */
	protected abstract String[] getClassBtnTFLabels();
	/**
	 * Instance class determines the true and false labels the category buttons use - if empty then no categories used
	 * @return array holding true(idx0) and false(idx1) labels for buttons to control display of category-based segment
	 */
	protected abstract String[] getCategoryBtnTFLabels();

	/**
	 * This will return instance class-based true and false labels for save segment data.  if empty then no segment saving possible
	 * @return array holding true(idx0) and false(idx1) labels for buttons to control saving of segment data
	 */
	protected abstract String[] getSegmentSaveBtnTFLabels();
	
	//initialize structure to hold modifiable menu regions
	@Override
	protected final void setupGUIObjsAras(){		
		
		ArrayList<Object[]> tmpUIObjArray = new ArrayList<Object[]>();
		//tmpBtnNamesArray.add(new Object[]{"Building SOM","Build SOM ",buildSOMExe});
		tmpUIObjArray.add(new Object[] {new double[]{0.0, uiMapTrainFtrTypeList.length-1, 1.0}, 1.0, "Train Data Frmt", new boolean[]{true, true, true}});   //uiTrainDataFrmtIDX                                                                        
		tmpUIObjArray.add(new Object[] {new double[]{0.0, uiMapTestFtrTypeList.length-1, 1.0}, 2.0, "Data Mapping Frmt", new boolean[]{true, true, true}});   //uiTestDataFrmtIDX                                                                         
		tmpUIObjArray.add(new Object[] {new double[]{1.0, 100.0, 1.0}, 100.0,	"Data % To Train", new boolean[]{true, false, true}});   //uiTrainDatPartIDX                                                                         
		tmpUIObjArray.add(new Object[] {new double[]{1.0, 120.0, 10}, 10.0, "# Map Rows", new boolean[]{true, false, true}});   //uiMapRowsIDX 	 		                                                                    
		tmpUIObjArray.add(new Object[] {new double[]{1.0, 120.0, 10}, 10.0, "# Map Columns", new boolean[]{true, false, true}});   //uiMapColsIDX	 		                                                                    
		tmpUIObjArray.add(new Object[] {new double[]{1.0, 200.0, 10}, 10.0, "# Training Epochs", new boolean[]{true, false, true}});   //uiMapEpochsIDX		                                                                    
		tmpUIObjArray.add(new Object[] {new double[]{0.0, uiMapShapeList.length-1, 1},0.0, "Map Node Shape", new boolean[]{true, true, true}});   //uiMapShapeIDX	 		                                                                    
		tmpUIObjArray.add(new Object[] {new double[]{0.0, uiMapBndsList.length-1, 1},1.0, "Map Boundaries",	new boolean[]{true, true, true}});   //uiMapBndsIDX	 		                                                                    
		tmpUIObjArray.add(new Object[] {new double[]{0.0, uiMapKTypList.length-1, .05},2.0, "Dense/Sparse (C/G)PU",new boolean[]{true, true, true}});   //uiMapKTypIDX	 		                                                                    
		tmpUIObjArray.add(new Object[] {new double[]{0.0, uiMapNHoodList.length-1, 1},0.0, "Neighborhood Func", new boolean[]{true, true, true}});   //uiMapNHdFuncIDX		                                                                    
		tmpUIObjArray.add(new Object[] {new double[]{0.0, uiMapRadClList.length-1, 1},0.0, "Radius Cooling", new boolean[]{true, true, true}});   //uiMapRadCoolIDX		                                                                    
		tmpUIObjArray.add(new Object[] {new double[]{0.0, uiMapLrnClList.length-1, 1},0.0, "Learn rate Cooling", new boolean[]{true, true, true}});   //uiMapLrnCoolIDX		                                                                    
		tmpUIObjArray.add(new Object[] {new double[]{0.001, 10.0, 0.001}, 1.0, "Start Learn Rate", new boolean[]{false, false, true}});   //uiMapLrnStIDX	 		                                                                    
		tmpUIObjArray.add(new Object[] {new double[]{0.001, 1.0, 0.001}, 0.1, "End Learn Rate", new boolean[]{false, false, true}});   //uiMapLrnEndIDX		                                                                    
		tmpUIObjArray.add(new Object[] {new double[]{2.0, 300.0, 1.0},	 20.0, "Start Cool Radius", new boolean[]{true, false, true}});   //uiMapRadStIDX	 	# nodes	                                                                
		tmpUIObjArray.add(new Object[] {new double[]{1.0, 10.0, 1.0},	 1.0, "End Cool Radius", new boolean[]{true, false, true}});   //uiMapRadEndIDX		# nodes	                                                            
		tmpUIObjArray.add(new Object[] {new double[]{0, uiMapDrawExToBmuTypeList.length-1, 1.0}, 0.0, "Ex Type For Node BMU", new boolean[]{true, true, true}});   //uiMapNodeBMUTypeToDispIDX                                                                 
		tmpUIObjArray.add(new Object[] {new double[]{0.0, 1.0, .01}, (double)SOM_MapManager.getNodeInFtrWtSegThresh(), "Map Node Disp Wt Thresh", new boolean[]{false, false, true}});   //uiNodeWtDispThreshIDX                                                                     
		tmpUIObjArray.add(new Object[] {new double[]{0.0, 1.0, .001}, (double)SOM_MapManager.getNodeInUMatrixSegThresh(), "Segment UDist Thresh", new boolean[]{false, false, true}});   //uiNodeInSegThreshIDX//threshold of u-matrix weight for nodes to belong to same segment    
		tmpUIObjArray.add(new Object[] {new double[]{0.0, 1.0, .1},	 0.0, "Mouse Over JP Sens",	new boolean[]{false, false, true}});   //uiMseRegionSensIDX                                                                        
		
		//populate instancing application objects
		setupGUIObjsArasIndiv(tmpUIObjArray);
		
		int numGUIObjs = tmpUIObjArray.size();		
		guiMinMaxModVals = new double [numGUIObjs][3];
		guiStVals = new double[numGUIObjs];
		guiObjNames = new String[numGUIObjs];
		guiBoolVals = new boolean [numGUIObjs][4];
		uiVals = new double[numGUIObjs];//raw values
		for(int i =0;i<numGUIObjs; ++i) {
			guiMinMaxModVals[i] = (double[])tmpUIObjArray.get(i)[0];
			guiStVals[i] = (Double)tmpUIObjArray.get(i)[1];
			guiObjNames[i] = (String)tmpUIObjArray.get(i)[2];
			guiBoolVals[i] = (boolean[])tmpUIObjArray.get(i)[3];
			uiVals[i] = guiStVals[i];
		}
		
		//since horizontal row of UI comps, uiClkCoords[2] will be set in buildGUIObjs		
		guiObjs = new myGUIObj[numGUIObjs];			//list of modifiable gui objects

		buildGUIObjs(guiObjNames,guiStVals,guiMinMaxModVals,guiBoolVals,new double[]{xOff,yOff});			//builds a horizontal list of UI comps	
		
	}//setupGUIObjsAras

	/**
	 * Instancing class-specific (application driven) UI objects should be defined
	 * in this function.  Add an entry to tmpBtnNamesArray for each button, in the order 
	 * they are to be displayed
	 * @param tmpUIObjArray array list of Object arrays, where in each object array : 
	 * 			the first element double array of min/max/mod values
	 * 			the 2nd element is starting value
	 * 			the 3rd elem is label for object
	 * 			the 4th element is boolean array of {treat as int, has list values, value is sent to owning window}
	 */
	protected abstract void setupGUIObjsArasIndiv(ArrayList<Object[]> tmpUIObjArray);
	
	public final void resetUIVals(){for(int i=0; i<guiStVals.length;++i){				guiObjs[i].setVal(guiStVals[i]);		}}	
	
	///////////////////////////////////////////
	// map image init	
	private final void reInitMapCubicSegments() {		mapUMatrixCubicSegmentsImg = pa.createImage(mapCubicUMatrixImg.width,mapCubicUMatrixImg.height, pa.ARGB);}//ARGB to treat like overlay
	public final void initMapAras(int numFtrVals, int num2ndryMaps) {
		curMapImgIDX = 0;
		int format = pa.RGB; 
		int w = (int) (SOM_mapDims[0]/mapScaleVal), h = (int) (SOM_mapDims[1]/mapScaleVal);
		mapPerFtrWtImgs = new PImage[numFtrVals];
		for(int i=0;i<mapPerFtrWtImgs.length;++i) {			mapPerFtrWtImgs[i] = pa.createImage(w, h, format);	}		
		mapCubicUMatrixImg = pa.createImage(w, h, format);			
		reInitMapCubicSegments();
		//instancing-window specific initializations
		initMapArasIndiv(w,h, format,num2ndryMaps);
	}//initMapAras	
	
	protected abstract void initMapArasIndiv(int w, int h, int format, int num2ndFtrVals);
	
	///////////////////////////////////////////
	// end map image init		
	
	//set window-specific variables that are based on current visible screen dimensions
	protected final void setVisScreenDimsPriv() {
		float xStart = rectDim[0] + .5f*(curVisScrDims[0] - (curVisScrDims[1]-(2*xOff)));
		//start x and y and dimensions of full map visualization as function of visible window size;
		SOM_mapLoc = new float[]{xStart, rectDim[1] + yOff};
		//now build calc analysis offset struct
		setVisScreenDimsPriv_Indiv();
	}//calcAndSetMapLoc
	protected abstract void setVisScreenDimsPriv_Indiv();
	
	
	//this will be called in instancing class when # of priv flags is known - should call initPrivFlags(numPrivFlags);
	protected abstract void initAllPrivFlags();
	@Override
	public final void setPrivFlags(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		privFlags[flIDX] = (val ?  privFlags[flIDX] | mask : privFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case buildSOMExe 			: {break;}			//placeholder	
			case resetMapDefsIDX		: {if(val){resetUIVals(); setPrivFlags(resetMapDefsIDX,false);}}
			case mapDataLoadedIDX 		: {break;}			//placeholder				
			case mapUseChiSqDistIDX		: {//whether or not to use chi-squared (weighted) distance for features
				//turn off scaled ftrs if this is set
				mapMgr.setUseChiSqDist(val);
				break;}							
			case mapExclProdZeroFtrIDX		: {//whether or not distances between two datapoints assume that absent features in smaller-length datapoints are 0, or to ignore the values in the larger datapoints
				mapMgr.setMapExclZeroFtrs(val);
				break;}							
			case mapDrawTrainDatIDX		: {//draw training examples
				break;}							
			case mapDrawTestDatIDX		: {//draw testing examples
				break;}		
			case mapDrawWtMapNodesIDX		: {//draw map nodes
				if (val) {//turn off other node displays
					setPrivFlags(mapDrawPopMapNodesIDX, false);
					setPrivFlags(mapDrawAllMapNodesIDX, false);					
				}
				break;}							
			case mapDrawPopMapNodesIDX  : {				
				if (val) {//turn off other node displays
					setPrivFlags(mapDrawWtMapNodesIDX, false);
					setPrivFlags(mapDrawAllMapNodesIDX, false);					
				}
				break;}
			case mapDrawAllMapNodesIDX	: {//draw all map nodes, even empty
				if (val) {//turn off other node displays
					setPrivFlags(mapDrawPopMapNodesIDX, false);
					setPrivFlags(mapDrawWtMapNodesIDX, false);					
				}
				break;}	
			case mapDrawFtrWtSegMembersIDX :{
				if(val) {mapMgr.buildFtrWtSegmentsOnMap();}
				break;}
			case mapDrawClassSegmentsIDX		:{			break;}			
			case mapDrawCategorySegmentsIDX	:{			break;}		
			
			case saveAllSegmentMapsIDX : {
				if(val) {
					mapMgr.saveAllSegment_BMUReports();
					setPrivFlags(saveAllSegmentMapsIDX, false);
				}
				break;}			
			case mapDrawNodeLblIDX : {//whether or not to show labels of nodes being displayed				
				break;}
			case mapDrawUMatrixIDX :{//whether to show the UMatrix (distance between nodes) representation of the map - overrides per-ftr display
				break;}
			case mapDrawUMatSegMembersIDX : {//whether to show segment membership for zones of the map, using a color overlay
				if(val) {mapMgr.buildUMatrixSegmentsOnMap();}
				break;}
			case mapDrawUMatSegImgIDX : {
				if(val) {mapMgr.buildUMatrixSegmentsOnMap();}
				break;}
			case showSelRegionIDX		 : {//highlight a specific region of the map, either all nodes above a certain threshold for a chosen jp or jpgroup
				break;}
			case saveLocClrImgIDX : {break;}		//save image

			default			: {setPrivFlagsIndiv(idx,val);}
		}
	}//setFlag		
	protected abstract void setPrivFlagsIndiv(int idx, boolean val);
	
	//set flag values when finished building map, to speed up initial display
	public final void setFlagsDoneMapBuild(){
		setPrivFlags(mapDrawTrainDatIDX, false);
		setPrivFlags(mapDrawWtMapNodesIDX, false);
		setPrivFlags(mapDrawAllMapNodesIDX, false);
	}//setFlagsDoneMapBuild
	
	
	protected HashMap<String, Integer> getIntUIValMap(){
		HashMap<String, Integer> mapInts = new HashMap<String, Integer>(); 
		mapInts.put("mapCols", (int)this.guiObjs[uiMapColsIDX].getVal());
		mapInts.put("mapRows", (int)this.guiObjs[uiMapRowsIDX].getVal());
		mapInts.put("mapEpochs", (int)this.guiObjs[uiMapEpochsIDX].getVal());
		mapInts.put("mapKType", (int)this.guiObjs[uiMapKTypIDX].getVal());
		mapInts.put("mapStRad", (int)this.guiObjs[uiMapRadStIDX].getVal());
		mapInts.put("mapEndRad", (int)this.guiObjs[uiMapRadEndIDX].getVal());
		for (String key : mapInts.keySet()) {msgObj.dispMessage("SOMMapUIWin","buildNewSOMMap->getIntUIValMap","mapInts["+key+"] = "+mapInts.get(key), MsgCodes.info1);}		
		return mapInts;
	}
	
	protected HashMap<String, Float> getFloatUIValMap(){
		HashMap<String, Float> mapFloats = new HashMap<String, Float>(); 
		mapFloats.put("mapStLrnRate",(float)this.guiObjs[uiMapLrnStIDX].getVal());
		mapFloats.put("mapEndLrnRate",(float)this.guiObjs[uiMapLrnEndIDX].getVal());
		for (String key : mapFloats.keySet()) {msgObj.dispMessage("SOMMapUIWin","buildNewSOMMap->getFloatUIValMap","mapFloats["+key+"] = "+ String.format("%.4f",mapFloats.get(key)), MsgCodes.info1);}		
		return mapFloats;
	}
	
	protected HashMap<String, String> getStringUIValMap(){
		HashMap<String, String> mapStrings = new HashMap<String, String> ();
		mapStrings.put("mapGridShape", getUIListValStr(uiMapShapeIDX, (int)this.guiObjs[uiMapShapeIDX].getVal()));	
		mapStrings.put("mapBounds", getUIListValStr(uiMapBndsIDX, (int)this.guiObjs[uiMapBndsIDX].getVal()));	
		mapStrings.put("mapRadCool", getUIListValStr(uiMapRadCoolIDX, (int)this.guiObjs[uiMapRadCoolIDX].getVal()));	
		mapStrings.put("mapNHood", getUIListValStr(uiMapNHdFuncIDX, (int)this.guiObjs[uiMapNHdFuncIDX].getVal()));	
		mapStrings.put("mapLearnCool", getUIListValStr(uiMapLrnCoolIDX, (int)this.guiObjs[uiMapLrnCoolIDX].getVal()));	
		for (String key : mapStrings.keySet()) {msgObj.dispMessage("SOMMapUIWin","buildNewSOMMap->getStringUIValMap","mapStrings["+key+"] = "+mapStrings.get(key), MsgCodes.info1);}
		return mapStrings;
	}
	

	//first verify that new .lrn file exists, then
	//build new SOM_MAP map using UI-entered values, then load resultant data - map nodes, bmus to training data
	protected final void buildNewSOMMap(){
		msgObj.dispMessage("SOMMapUIWin","buildNewSOMMap","Starting Map Build", MsgCodes.info5);
		setPrivFlags(buildSOMExe, false);
		//send current UI values to map manager, load appropriate data, build directory structure and execute map
		boolean returnCode = mapMgr.loadTrainDataMapConfigAndBuildMap_UI(true, getIntUIValMap(), getFloatUIValMap(), getStringUIValMap());

		//returnCode is whether map was built and trained successfully
		setFlagsDoneMapBuild();
		msgObj.dispMessage("SOMMapUIWin","buildNewSOMMap","Map Build " + (returnCode ? "Completed Successfully." : "Failed due to error."), MsgCodes.info5);
		
	}//buildNewSOMMap		
	
	//update UI values from passed SOM_MAPDat object's current state
	public final void setUIValues(SOM_MapDat mapDat) {
		HashMap<String, Integer> mapInts = mapDat.getMapInts();
		HashMap<String, Float> mapFloats = mapDat.getMapFloats();
		HashMap<String, String> mapStrings = mapDat.getMapStrings();

		this.guiObjs[uiMapColsIDX].setVal(mapInts.get("mapCols"));
		this.guiObjs[uiMapRowsIDX].setVal(mapInts.get("mapRows"));
		this.guiObjs[uiMapEpochsIDX].setVal(mapInts.get("mapEpochs"));
		this.guiObjs[uiMapKTypIDX].setVal(mapInts.get("mapKType"));
		this.guiObjs[uiMapRadStIDX].setVal(mapInts.get("mapStRad"));
		this.guiObjs[uiMapRadEndIDX].setVal(mapInts.get("mapEndRad"));
		
		this.guiObjs[uiMapLrnStIDX].setVal(mapFloats.get("mapStLrnRate"));
		this.guiObjs[uiMapLrnEndIDX].setVal(mapFloats.get("mapEndLrnRate"));
		
		this.guiObjs[uiMapShapeIDX].setVal(getIdxFromListString(uiMapShapeIDX, mapStrings.get("mapGridShape")));	
		this.guiObjs[uiMapBndsIDX].setVal(getIdxFromListString(uiMapBndsIDX, mapStrings.get("mapBounds")));	
		this.guiObjs[uiMapRadCoolIDX].setVal(getIdxFromListString(uiMapRadCoolIDX, mapStrings.get("mapRadCool")));	
		this.guiObjs[uiMapNHdFuncIDX].setVal(getIdxFromListString(uiMapNHdFuncIDX, mapStrings.get("mapNHood")));	
		this.guiObjs[uiMapLrnCoolIDX].setVal(getIdxFromListString(uiMapLrnCoolIDX, mapStrings.get("mapLearnCool")));
				
	}//setUIValues
	
	protected final int getIDXofStringInArray(String[] ara, String tok) {
		for(int i=0;i<ara.length;++i) {			if(ara[i].equals(tok)) {return i;}		}		
		return -1;
	}
	
	protected final int getIdxFromListString(int UIidx, String dat) {
		switch(UIidx){//pa.score.staffs.size()
			case uiTrainDataFrmtIDX : {return getIDXofStringInArray(uiMapTrainFtrTypeList, dat);} 
			case uiTestDataFrmtIDX	: {return getIDXofStringInArray(uiMapTestFtrTypeList, dat);}
			case uiMapShapeIDX		: {return getIDXofStringInArray(uiMapShapeList, dat);} 
			case uiMapBndsIDX		: {return getIDXofStringInArray(uiMapBndsList, dat);} 
			case uiMapKTypIDX		: {return getIDXofStringInArray(uiMapKTypList, dat);} 
			case uiMapNHdFuncIDX	: {return getIDXofStringInArray(uiMapNHoodList, dat);} 
			case uiMapRadCoolIDX	: {return getIDXofStringInArray(uiMapRadClList, dat);} 
			case uiMapNodeBMUTypeToDispIDX : {return getIDXofStringInArray(uiMapDrawExToBmuTypeList, dat);}
			case uiMapLrnCoolIDX	: {return getIDXofStringInArray(uiMapLrnClList, dat);} 
			default : return getIdxFromListStringIndiv(UIidx, dat);
		}
	}//getIdxFromListString
	protected abstract int getIdxFromListStringIndiv(int UIidx, String dat);
	//if any ui values have a string behind them for display
	@Override
	public final String getUIListValStr(int UIidx, int validx) {		
		//msgObj.dispMessage("SOMMapUIWin","getUIListValStr","UIidx : " + UIidx + "  Val : " + validx );
		switch(UIidx){//pa.score.staffs.size()
			case uiTrainDataFrmtIDX 		: {return uiMapTrainFtrTypeList[validx % uiMapTrainFtrTypeList.length];}
			case uiTestDataFrmtIDX			: {return uiMapTestFtrTypeList[validx % uiMapTestFtrTypeList.length];}
			case uiMapShapeIDX				: {return uiMapShapeList[validx % uiMapShapeList.length]; }
			case uiMapBndsIDX				: {return uiMapBndsList[validx % uiMapBndsList.length]; }
			case uiMapKTypIDX				: {return uiMapKTypList[validx % uiMapKTypList.length]; }
			case uiMapNHdFuncIDX			: {return uiMapNHoodList[validx % uiMapNHoodList.length]; }
			case uiMapRadCoolIDX			: {return uiMapRadClList[validx % uiMapRadClList.length]; }
			case uiMapNodeBMUTypeToDispIDX 	: {return uiMapDrawExToBmuTypeList[validx %  uiMapDrawExToBmuTypeList.length];}
			case uiMapLrnCoolIDX			: {return uiMapLrnClList[validx % uiMapLrnClList.length]; }	
			default 						: {return getUIListValStrIndiv(UIidx, validx);}
		}
	}//getUIListValStr
	protected abstract String getUIListValStrIndiv(int UIidx, int validx);
	
	///////////////////////////////
	// map <--> ui sync functions
	//
	@Override
	public void setMapDataVal_Integer(int UIidx, double val) {	mapMgr.updateMapDatFromUI_Integer(getMapKeyStringFromUIidx(UIidx), (int) val);}	
	@Override
	public void setMapDataVal_Float(int UIidx, double val) {	mapMgr.updateMapDatFromUI_Float(getMapKeyStringFromUIidx(UIidx), (float) val);}
	@Override
	public void setMapDataVal_String(int UIidx, double val) {	mapMgr.updateMapDatFromUI_String(getMapKeyStringFromUIidx(UIidx), getUIListValStr(UIidx, (int)val));}
	
	// set UI vals from map mgr - these are changes resulting from non-UI made changes to map
	@Override
	public void updateUIDataVal_Integer(String key, Integer val) {
		if(!isMapNameOfType(mapDatNames_Ints, key)) {
			msgObj.dispMessage("SOMMapUIWin","updateUIDataVal_Integer","Attempting to set UI object with unknown Key : " +key + " using integer value " + val +".",MsgCodes.warning1);	
			return;}
		Integer uiObjIDX = getUIidxFromMapKeyString(key);
		uiVals[uiObjIDX] = guiObjs[uiObjIDX].setVal(val);
		switch (uiObjIDX) {
			//integer values
			case uiMapRowsIDX 	    : {guiObjs[uiMapRadStIDX].setVal(.5*Math.min(val, guiObjs[uiMapColsIDX].getVal()));	break;}
			case uiMapColsIDX	    : {guiObjs[uiMapRadStIDX].setVal(.5*Math.min(guiObjs[uiMapRowsIDX].getVal(), val));break;}
			case uiMapEpochsIDX	    : {break;}
			case uiMapKTypIDX	    : {break;}
			case uiMapRadStIDX	    : {if(val <= guiObjs[uiMapRadEndIDX].getVal()+guiMinMaxModVals[uiObjIDX][2]) {uiVals[uiObjIDX] = guiObjs[uiObjIDX].setVal(guiObjs[uiMapRadEndIDX].getVal()+guiMinMaxModVals[uiObjIDX][2]);}break;}
			case uiMapRadEndIDX	    : {if(val >= guiObjs[uiMapRadStIDX].getVal()-guiMinMaxModVals[uiObjIDX][2]) { uiVals[uiObjIDX] = guiObjs[uiObjIDX].setVal(guiObjs[uiMapRadStIDX].getVal()-guiMinMaxModVals[uiObjIDX][2]);}break;}
		}
	}//setUIDataVal_Integer	

	@Override
	public void updateUIDataVal_Float(String key, Float val) {
		if(!isMapNameOfType(mapDatNames_Floats, key)) {
			msgObj.dispMessage("SOMMapUIWin","updateUIDataVal_Float","Attempting to set UI object with unknown Key : " +key + " using integer value " + val +".",MsgCodes.warning1);	
			return;}
		Integer uiObjIDX = getUIidxFromMapKeyString(key);
		uiVals[uiObjIDX] = guiObjs[uiObjIDX].setVal(val);
		switch (uiObjIDX) {
		case uiMapLrnStIDX	    : {	if(val <= guiObjs[uiMapLrnEndIDX].getVal()+guiMinMaxModVals[uiObjIDX][2]) {	uiVals[uiObjIDX] = guiObjs[uiObjIDX].setVal(guiObjs[uiMapLrnEndIDX].getVal()+guiMinMaxModVals[uiObjIDX][2]);}break;}
		case uiMapLrnEndIDX	    : {	if(val >= guiObjs[uiMapLrnStIDX].getVal()-guiMinMaxModVals[uiObjIDX][2]) {	uiVals[uiObjIDX] = guiObjs[uiObjIDX].setVal(guiObjs[uiMapLrnStIDX].getVal()-guiMinMaxModVals[uiObjIDX][2]);}break;}
		}
	}//setUIDataVal_Float
	
	@Override
	public void updateUIDataVal_String(String key, String val) {
		if(!isMapNameOfType(mapDatNames_Strings, key)) {
			msgObj.dispMessage("SOMMapUIWin","updateUIDataVal_String","Attempting to set UI object with unknown Key : " +key + " using String value " + val +".",MsgCodes.warning1);
			return;}
		Integer uiObjIDX = getUIidxFromMapKeyString(key);
		uiVals[uiObjIDX] = guiObjs[uiObjIDX].setVal(getIdxFromListString(uiObjIDX,val));
	}//setUIDataVal_String
	
	///////////////////////////////
	// end map <--> ui sync functions
	//
	//this sets the window's uiVals array to match the ui object's internal state - if object is set but this is not called, subordinate code for object (in switch below) will not get run
	@Override
	protected final void setUIWinVals(int UIidx) {
		double val = guiObjs[UIidx].getVal(); 
		if(uiVals[UIidx] != val){uiVals[UIidx] = val;} else {return;}//set values in raw array and only proceed if values have changed
		//int intVal = (int)val;
		switch(UIidx){
			//integer values
			case uiMapRowsIDX 	    : {setMapDataVal_Integer(UIidx,val);guiObjs[uiMapRadStIDX].setVal(.5*Math.min(val, guiObjs[uiMapColsIDX].getVal()));	break;}
			case uiMapColsIDX	    : {setMapDataVal_Integer(UIidx,val);guiObjs[uiMapRadStIDX].setVal(.5*Math.min(guiObjs[uiMapRowsIDX].getVal(), val));break;}
			case uiMapEpochsIDX	    : {setMapDataVal_Integer(UIidx,val);break;}
			case uiMapKTypIDX	    : {setMapDataVal_Integer(UIidx,val);break;}
			case uiMapRadStIDX	    : {
				if(val <= guiObjs[uiMapRadEndIDX].getVal()+guiMinMaxModVals[UIidx][2]) {val = guiObjs[UIidx].setVal(guiObjs[uiMapRadEndIDX].getVal()+guiMinMaxModVals[UIidx][2]);uiVals[UIidx] = val;}
				setMapDataVal_Integer(UIidx,val);		break;}
			case uiMapRadEndIDX	    : {
				if(val >= guiObjs[uiMapRadStIDX].getVal()-guiMinMaxModVals[UIidx][2]) { val = guiObjs[UIidx].setVal(guiObjs[uiMapRadStIDX].getVal()-guiMinMaxModVals[UIidx][2]);uiVals[UIidx] = val;}
				setMapDataVal_Integer(UIidx,val);		break;}
			//end of integer values |start of float values
			case uiMapLrnStIDX	    : {
				if(val <= guiObjs[uiMapLrnEndIDX].getVal()+guiMinMaxModVals[UIidx][2]) {val = guiObjs[UIidx].setVal(guiObjs[uiMapLrnEndIDX].getVal()+guiMinMaxModVals[UIidx][2]);uiVals[UIidx] = val;}			
				setMapDataVal_Float(UIidx,val);			break;}
			case uiMapLrnEndIDX	    : {
				if(val >= guiObjs[uiMapLrnStIDX].getVal()-guiMinMaxModVals[UIidx][2]) {	val = guiObjs[UIidx].setVal(guiObjs[uiMapLrnStIDX].getVal()-guiMinMaxModVals[UIidx][2]);uiVals[UIidx] = val;}		
				setMapDataVal_Float(UIidx,val);			break;}
			//end of float values | start of string/list values
			case uiMapNHdFuncIDX	: {setMapDataVal_String(UIidx,val); break;}
			case uiMapRadCoolIDX	: {setMapDataVal_String(UIidx,val); break;}
			case uiMapLrnCoolIDX	: {setMapDataVal_String(UIidx,val); break;}
			case uiMapShapeIDX	    : {setMapDataVal_String(UIidx,val); break;}
			case uiMapBndsIDX	    : {setMapDataVal_String(UIidx,val); break;}
			//end map arg-related string/list values
			
			case uiTrainDataFrmtIDX : {//format of training data
				mapMgr.setCurrentTrainDataFormat((int)(this.guiObjs[uiTrainDataFrmtIDX].getVal()));
				break;}
			case uiTestDataFrmtIDX : {
				mapMgr.setCurrentTestDataFormat((int)(this.guiObjs[uiTestDataFrmtIDX].getVal()));
				break;}
			case uiTrainDatPartIDX : {break;}
			case uiNodeWtDispThreshIDX : {
				mapNodeWtDispThresh = (float)(this.guiObjs[uiNodeWtDispThreshIDX].getVal());
				SOM_MapManager.setNodeInFtrWtSegThresh(mapNodeWtDispThresh);				
				break;}
			case uiNodeInSegThreshIDX :{		//used to determine threshold of value for setting membership in a segment/cluster
				SOM_MapManager.setNodeInUMatrixSegThresh((float)(this.guiObjs[uiNodeInSegThreshIDX].getVal()));
				mapMgr.buildUMatrixSegmentsOnMap();
				break;}
			case uiMapNodeBMUTypeToDispIDX : {//type of examples being mapped to each map node to display
				mapNodeDispType = ExDataType.getVal((int)(this.guiObjs[uiMapNodeBMUTypeToDispIDX].getVal()));
				break;}			
			case uiMseRegionSensIDX : {
				break;}
			default : {setUIWinValsIndiv(UIidx);}
		}
	}//setUIWinVals
	protected abstract void setUIWinValsIndiv(int UIidx);
	
	protected float getTrainTestDatPartition() {	return (float)(.01*this.guiObjs[uiTrainDatPartIDX].getVal());}
	
	
	/////////////////////////////////////////
	// draw routines
	
	@Override
	protected final void drawMe(float animTimeMod) {
		drawSetDispFlags();
		setPrivFlags(mapDataLoadedIDX,mapMgr.isMapDrawable());
		drawMap();		
		if(getPrivFlags(buildSOMExe)){buildNewSOMMap();}
	}
	protected abstract void drawSetDispFlags();
	
	private void drawMap(){		
		//draw map rectangle
		pa.pushMatrix();pa.pushStyle();
		//instance-specific drawing
		drawMapIndiv();
		if(getPrivFlags(mapDataLoadedIDX)){drawMapRectangle();}	
		pa.popStyle();pa.popMatrix();
	}//drawMap()	
	protected abstract void drawMapIndiv();
	
	protected final void drawMseOverData() {
		pa.pushMatrix();pa.pushStyle();
			pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
			mseOvrData.drawMeLblMap((my_procApplet)pa);
		pa.popStyle();pa.popMatrix();		
	}
	
	//draw map rectangle and map nodes
	protected final void drawMapRectangle() {		
		pa.pushMatrix();pa.pushStyle();
			pa.noLights();
			pa.scale(mapScaleVal);
			PImage tmpImg;
			int curImgNum;
			if(getPrivFlags(mapDrawUMatrixIDX)) {				
				tmpImg =  mapCubicUMatrixImg;
				curImgNum = -1;
			} else {
				tmpImg = mapPerFtrWtImgs[curMapImgIDX];		
				curImgNum = curMapImgIDX;
			}
			pa.image(tmpImg,SOM_mapLoc[0]/mapScaleVal,SOM_mapLoc[1]/mapScaleVal); if(getPrivFlags(saveLocClrImgIDX)){tmpImg.save(mapMgr.getSOMLocClrImgForFtrFName(curImgNum));  setPrivFlags(saveLocClrImgIDX,false);}			
		if(getPrivFlags(mapDrawUMatSegImgIDX)) {pa.image(mapUMatrixCubicSegmentsImg,SOM_mapLoc[0]/mapScaleVal,SOM_mapLoc[1]/mapScaleVal);}//image synthesized (smoother)
		pa.popStyle();pa.popMatrix(); 
		pa.pushMatrix();pa.pushStyle();
			boolean drawLbl = getPrivFlags(mapDrawNodeLblIDX);
			pa.translate(SOM_mapLoc[0],SOM_mapLoc[1],0);	
			if(getPrivFlags(mapDrawTrainDatIDX)){			mapMgr.drawTrainData(pa);}	
			if(getPrivFlags(mapDrawTestDatIDX)) {			mapMgr.drawTestData(pa);}
			//draw nodes by population
			if(getPrivFlags(mapDrawPopMapNodesIDX)) {	if(drawLbl) {mapMgr.drawPopMapNodes(pa, mapNodeDispType);} else {mapMgr.drawPopMapNodesNoLbl(pa, mapNodeDispType);}}
			
			if(curImgNum == -1) {			drawSegmentsUMatrixDisp();}
			//instance-specific stuff to draw on map, after nodes are drawn
			drawMapRectangleIndiv(curImgNum);
			//if draw all map nodes
			if(getPrivFlags(mapDrawAllMapNodesIDX)){	if(drawLbl) {mapMgr.drawAllNodes(pa);} else {mapMgr.drawAllNodesNoLbl(pa);} }
			pa.lights();
		pa.popStyle();pa.popMatrix();	
	}//drawMapRectangle
	
	protected abstract void drawMapRectangleIndiv(int curImgNum);
	//draw various segments in UMatrix Display
	protected void drawSegmentsUMatrixDisp() {
		if(getPrivFlags(mapDrawUMatSegMembersIDX)) {		mapMgr.drawUMatrixSegments(pa);}
		if(getPrivFlags(mapDrawFtrWtSegMembersIDX)) {		mapMgr.drawAllFtrWtSegments(pa, mapNodeWtDispThresh);}	//draw all segments - will overlap here, might look like garbage		
		if(getPrivFlags(mapDrawClassSegmentsIDX)) {	 		mapMgr.drawAllClassSegments(pa);}
		if(getPrivFlags(mapDrawCategorySegmentsIDX)) { 		mapMgr.drawAllCategorySegments(pa);}

		drawSegmentsUMatrixDispIndiv();
	}
	/**
	 * Instancing class-specific segments to render during UMatrix display
	 */
	protected abstract void drawSegmentsUMatrixDispIndiv();
	
	protected final void drawSegmentsFtrWeightDisp(int ftrIDX) {if(getPrivFlags(mapDrawFtrWtSegMembersIDX)) {		mapMgr.drawFtrWtSegments(pa, mapNodeWtDispThresh, ftrIDX);}}//drawSegmentsFtrWeightDisp
	
	protected final void drawClassCatDisp(int classVal, int categoryVal) {
		if(getPrivFlags(mapDrawClassSegmentsIDX)) {	 		mapMgr.drawClassSegments(pa,classVal);	}		
		if(getPrivFlags(mapDrawCategorySegmentsIDX)) { 		mapMgr.drawCategorySegments(pa,categoryVal);	}
	}//drawSegmentsFtrWeightDisp
	
	

	/////////////////////////////////////////
	// end draw routines
	
	
	//val is 0->256
	private final int getDataClrFromFloat(Float val) {
		int ftr = Math.round(val);		
		int clrVal = ((ftr & 0xff) << 16) + ((ftr & 0xff) << 8) + (ftr & 0xff);
		return clrVal;
	}//getDataClrFromFloat
	
	//make color based on ftr value at particular index
	//jpIDX is index in feature vector we are querying
	//call this if map is trained on scaled or normed ftr data
	private final int getDataClrFromFtrVec(TreeMap<Integer, Float> ftrMap, Integer jpIDX) {
		Float ftrVal = ftrMap.get(jpIDX);
//		if(ftrVal == null) {	ftrVal=0.0f;		}
//		if (minFtrValSeen[jpIDX] > ftrVal) {minFtrValSeen[jpIDX]=ftrVal;}
//		else if (maxFtrValSeen[jpIDX] < ftrVal) {maxFtrValSeen[jpIDX]=ftrVal;}
		int ftr = 0;
		if(ftrVal != null) {	ftr = Math.round(ftrVal);		}
		int clrVal = ((ftr & 0xff) << 16) + ((ftr & 0xff) << 8) + (ftr & 0xff);
		return clrVal;
	}//getDataClrFromFtrVec
	
	//set colors of image of umatrix map
	public final void setMapUMatImgClrs() {
		mapCubicUMatrixImg.loadPixels();
		//float[] c;	
		//mapUMatrixImg
		//single threaded exec
		for(int y = 0; y<mapCubicUMatrixImg.height; ++y){
			int yCol = y * mapCubicUMatrixImg.width;
			for(int x = 0; x < mapCubicUMatrixImg.width; ++x){
				//c = getMapNodeLocFromPxlLoc(x, y,mapScaleVal);
				Float valC = mapMgr.getBiCubicInterpUMatVal(getMapNodeLocFromPxlLoc(x, y,mapScaleVal));
				mapCubicUMatrixImg.pixels[x+yCol] = getDataClrFromFloat(valC);
			}
		}
		mapCubicUMatrixImg.updatePixels();	
	}//setMapUMatImgClrs
	//set colors of image of umatrix map
	public final void setMapSegmentImgClrs_UMatrix() {
		reInitMapCubicSegments();//reinitialize map array
		mapUMatrixCubicSegmentsImg.loadPixels();
		//float[] c;	
		//single threaded exec
		for(int y = 0; y<mapUMatrixCubicSegmentsImg.height; ++y){
			int yCol = y * mapUMatrixCubicSegmentsImg.width;
			for(int x = 0; x < mapUMatrixCubicSegmentsImg.width; ++x){
				//c = getMapNodeLocFromPxlLoc(x, y,mapScaleVal);
				int valC = mapMgr.getUMatrixSegementColorAtPxl(getMapNodeLocFromPxlLoc(x, y,mapScaleVal));
				mapUMatrixCubicSegmentsImg.pixels[x+yCol] = valC;
			}
		}
		mapUMatrixCubicSegmentsImg.updatePixels();
	}//setMapUMatImgClrs
	
	//sets colors of background image of map -- partition pxls for each thread
	public final void setMapImgClrs(){ //mapRndClrImg
		float[] c;		
		int stTime = pa.millis();
		for (int i=0;i<mapPerFtrWtImgs.length;++i) {	mapPerFtrWtImgs[i].loadPixels();}//needed to retrieve pixel values
		//build uMatrix image
		setMapUMatImgClrs();
		//build segmentation image based on UMatrix distance
		setMapSegmentImgClrs_UMatrix();
		//check if single threaded
		int numThds = mapMgr.getNumUsableThreads();
		boolean mtCapable = mapMgr.isMTCapable();
		if(mtCapable) {				
			//partition into mapMgr.numUsableThreads threads - split x values by this #, use all y values
			int numPartitions = numThds;
			int numXPerPart = mapPerFtrWtImgs[0].width / numPartitions;			
			int numXLastPart = (mapPerFtrWtImgs[0].width - (numXPerPart*numPartitions)) + numXPerPart;
			List<Future<Boolean>> mapImgFtrs = new ArrayList<Future<Boolean>>();
			List<SOMFtrMapVisImgBuilder> mapImgBuilders = new ArrayList<SOMFtrMapVisImgBuilder>();
			int[] xVals = new int[] {0,0};
			int[] yVals = new int[] {0,mapPerFtrWtImgs[0].height};
			//each thread builds columns of every map
			for (int i=0; i<numPartitions-1;++i) {	
				xVals[1] += numXPerPart;
				mapImgBuilders.add(new SOMFtrMapVisImgBuilder(mapMgr, mapPerFtrWtImgs, xVals, yVals, mapScaleVal));
				xVals[0] = xVals[1];				
			}
			//last one
			xVals[1] += numXLastPart;
			mapImgBuilders.add(new SOMFtrMapVisImgBuilder(mapMgr, mapPerFtrWtImgs, xVals, yVals, mapScaleVal));
			mapMgr.invokeSOMFtrDispBuild(mapImgBuilders);
			//try {mapImgFtrs = pa.th_exec.invokeAll(mapImgBuilders);for(Future<Boolean> f: mapImgFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }					
		} else {
			//single threaded exec
			for(int y = 0; y<mapPerFtrWtImgs[0].height; ++y){
				int yCol = y * mapPerFtrWtImgs[0].width;
				for(int x = 0; x < mapPerFtrWtImgs[0].width; ++x){
					int pxlIDX = x+yCol;
					//c = getMapNodeLocFromPxlLoc(x, y,mapScaleVal);
					TreeMap<Integer, Float> ftrs = mapMgr.getInterpFtrs(getMapNodeLocFromPxlLoc(x, y,mapScaleVal));
					for (Integer jp : ftrs.keySet()) {mapPerFtrWtImgs[jp].pixels[pxlIDX] = getDataClrFromFtrVec(ftrs, jp);}
				}
			}
		}
		for (int i=0;i<mapPerFtrWtImgs.length;++i) {	mapPerFtrWtImgs[i].updatePixels();		}
		int endTime = pa.millis();
		msgObj.dispMessage("SOMMapUIWin", "setMapImgClrs", "Time to build all vis imgs : "  + ((endTime-stTime)/1000.0f) + "s | Threading : " + (mtCapable ? "Multi ("+numThds+")" : "Single" ), MsgCodes.info5);
	}//setMapImgClrs
	
	//get x and y locations relative to upper corner of map
	public final float getSOMRelX (float x){return (x - SOM_mapLoc[0]);}
	public final float getSOMRelY (float y){return (y - SOM_mapLoc[1]);}	
	
	//given pixel location relative to upper left corner of map, return map node float - this measures actual distance in map node coords
	//so rounding to ints give map node tuple coords, while float gives interp between neighbors
	protected final float[] getMapNodeLocFromPxlLoc(float mapPxlX, float mapPxlY, float sclVal){	return new float[]{(sclVal* mapPxlX * mapMgr.getNodePerPxlCol()) - .5f, (sclVal* mapPxlY * mapMgr.getNodePerPxlRow()) - .5f};}	
	//check whether the mouse is over a legitimate map location
	protected final boolean chkMouseOvr(int mouseX, int mouseY){		
		float mapMseX = getSOMRelX(mouseX), mapMseY = getSOMRelY(mouseY);//, mapLocX = mapX * mapMseX/mapDims[2],mapLocY = mapY * mapMseY/mapDims[3] ;
		if((mapMseX >= 0) && (mapMseY >= 0) && (mapMseX < SOM_mapDims[0]) && (mapMseY < SOM_mapDims[1])){
			float[] mapNLoc=getMapNodeLocFromPxlLoc(mapMseX,mapMseY, 1.0f);
			//msgObj.dispInfoMessage("SOMMapUIWin","chkMouseOvr","In Map : Mouse loc : " + mouseX + ","+mouseY+ "\tRel to upper corner ("+  mapMseX + ","+mapMseY +") | mapNLoc : ("+mapNLoc[0]+","+ mapNLoc[1]+")" );
			mseOvrData = getDataPointAtLoc(mapNLoc[0], mapNLoc[1], new myPointf(mapMseX, mapMseY,0));			
			return true;
		} else {
			mapMgr.setMseDataExampleNone();
			mseOvrData = null;
			return false;
		}
	}//chkMouseOvr
	
	//get datapoint at passed location in map coordinates (so should be in frame of map's upper right corner) - assume map is square and not hex
	protected final SOM_MseOvrDisplay getDataPointAtLoc(float x, float y, myPointf locPt){//, boolean useScFtrs){
		float sensitivity = (float) guiObjs[uiMseRegionSensIDX].getVal();
		SOM_MseOvrDisplay dp; 
		SOMMapNode nearestNode;
		if (getPrivFlags(mapDrawClassSegmentsIDX)) {			//disp class probs at nearest node
			//find nearest map node to location
			nearestNode = mapMgr.getMapNodeByCoords(new Tuple<Integer,Integer> ((int)(x+.5f), (int)(y+.5f)));
			dp = mapMgr.setMseDataExampleClassProb(locPt,nearestNode,sensitivity);
		} else if (getPrivFlags(mapDrawCategorySegmentsIDX)) {	//disp category probs at nearest node			
			nearestNode = mapMgr.getMapNodeByCoords(new Tuple<Integer,Integer> ((int)(x+.5f), (int)(y+.5f)));
			dp = mapMgr.setMseDataExampleCategoryProb(locPt,nearestNode,sensitivity);
			
		} else if (getPrivFlags(mapDrawPopMapNodesIDX)) { //if showing node pop, mouse over should show actual population
			nearestNode = mapMgr.getMapNodeByCoords(new Tuple<Integer,Integer> ((int)(x+.5f), (int)(y+.5f)));
			dp = mapMgr.setMseDataExampleNodePop(locPt,nearestNode,sensitivity);
		} else {//show mouse data based on which display is currently shown
			if (getPrivFlags(mapDrawUMatrixIDX)) {		
				dp = mapMgr.setMseDataExampleDists(locPt, mapMgr.getBiCubicInterpUMatVal(new float[] {x, y}), sensitivity);				
			} else {
				TreeMap<Integer, Float> ftrs = mapMgr.getInterpFtrs(new float[] {x, y});
				if(ftrs == null) {return null;} 
				dp = mapMgr.setMseDataExampleFtrs(locPt, ftrs, sensitivity);				
			}
		}
		dp.setMapLoc(locPt);
		return dp;
	}//getDataPointAtLoc
	
	//return strings for directory names and for individual file names that describe the data being saved.  used for screenshots, and potentially other file saving
	//first index is directory suffix - should have identifying tags based on major/archtypical component of sim run
	//2nd index is file name, should have parameters encoded
	@Override
	protected String[] getSaveFileDirNamesPriv() {
		String dirString="", fileString ="";
		//for(int i=0;i<uiAbbrevList.length;++i) {fileString += uiAbbrevList[i]+"_"+ (uiVals[i] > 1 ? ((int)uiVals[i]) : uiVals[i] < .0001 ? String.format("%6.3e", uiVals[i]) : String.format("%3.3f", uiVals[i]))+"_";}
		return new String[]{dirString,fileString};	
	}
	@Override
	public void hndlFileLoad(File file, String[] vals, int[] stIdx) {
		//if wanting to load/save UI values, uncomment this call and similar in hndlFileSave 
		//hndlFileLoad_GUI(vals, stIdx);
		//loading in grade data from grade file - vals holds array of strings, expected to be comma sep values, for a single class, with student names and grades
	}
	@Override
	public ArrayList<String> hndlFileSave(File file) {
		ArrayList<String> res = new ArrayList<String>();
		//if wanting to load/save UI values, uncomment this call and similar in hndlFileLoad 
		//res = hndlFileSave_GUI();
		//saving student grades to a file for a single class - vals holds array of strings, expected to be comma sep values, for a single class, with student names and grades		
		return res;
	}
	@Override
	protected myPoint getMsePtAs3DPt(int mouseX, int mouseY){return pa.P(mouseX,mouseY,0);}
	@Override
	protected void snapMouseLocs(int oldMouseX, int oldMouseY, int[] newMouseLoc){}//not a snap-to window
	@Override
	protected void processTrajIndiv(myDrawnSmplTraj drawnNoteTraj){		}
	@Override
	protected void endShiftKeyI() {}
	@Override
	protected void endAltKeyI() {}
	@Override
	protected void endCntlKeyI() {}
	@Override
	protected void addSScrToWinIndiv(int newWinKey){}
	@Override
	protected void addTrajToScrIndiv(int subScrKey, String newTrajKey){}
	@Override
	protected void delSScrToWinIndiv(int idx) {}	
	@Override
	protected void delTrajToScrIndiv(int subScrKey, String newTrajKey) {}
	//resize drawn all trajectories
	@Override
	protected void resizeMe(float scale) {}
	@Override
	protected void closeMe() {}
	@Override
	protected void showMe() {
		//pa.setMenuDbgBtnNames(menuDbgBtnNames);	
		setCustMenuBtnNames();
	}

}//SOMMapUIWin
