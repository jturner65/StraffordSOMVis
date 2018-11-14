package SOM_Strafford_PKG;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

import processing.core.PImage;



//window that accepts trajectory editing
public class mySOMMapUIWin extends myDispWindow {
	
	public SOMMapData SOM_Data;
	
	public static final int 
		buildSOMExe 			= 0,			//command to initiate SOM-building
		resetMapDefsIDX			= 1,			//reset default UI values for map
		mapDataLoadedIDX		= 2,			//whether map has been loaded or not	
		mapLoadFtrBMUsIDX 		= 3,			//whether or not to load the best matching units for each feature - this is a large construct so load only if necessary
		mapUseSclFtrDistIDX 	= 4,			//whether or not to use the scaled (0-1) ftrs or the unscaled features for distance measures
		mapUseChiSqDistIDX		= 5,			//whether or not to use chi-squared (weighted) distance for features
		mapSetSmFtrZeroIDX		= 6,			//whether or not distances between two datapoints assume that absent features in smaller-length datapoints are 0, or to ignore the values in the larger datapoints
		//display/interaction
		mapDrawTrainDatIDX		= 7,			//draw training examples
		mapDrawTrDatLblIDX		= 8,			//draw labels for training samples
		mapDrawMapNodesIDX		= 9,			//draw map nodes
		mapDrawAllMapNodesIDX	= 10,			//draw all map nodes, even empty
		//mapShowLocClrIDX 		= 11,			//show img built of map with each pxl clr built from the 1st 3 features of the interpolated point at that pxl between the map nodes
		showSelRegionIDX		= 11,			//highlight a specific region of the map, either all nodes above a certain threshold for a chosen jp or jpgroup
		showSelJPIDX			= 12, 			//if showSelRegionIDX == true, then this will show either a selected jp or jpgroup
		//train/test data management
		somTrainDataLoadedIDX	= 13,			//whether data used to build map has been loaded yet
		saveLocClrImgIDX		= 14,
		appendTrainDataIDX		= 15,			//append to existing processed training data to retrain map with expanded data. NOTE : may have issues with duplication of records 
		useOnlyEvntsToTrainIDX  = 16;			//only use records that have event jpgs/jps to train, otherwise use records that also have jpgs/jps only specified in prospect db
	
	public static final int numPrivFlags = 17;
	
	//SOM map list options
	public String[] 
		uiMapShapeList = new String[] {"rectangular","hexagonal"},
		uiMapBndsList = new String[] {"planar","toroid"},
		uiMapKTypList = new String[] {"Dense CPU", "Dense GPU", "Sparse CPU"},
		uiMapNHoodList = new String[] {"gaussian","bubble"},
		uiMapRadClList = new String[] {"linear","exponential"},
		uiMapLrnClList = new String[] {"linear","exponential"},		
		uiMapTrainFtrTypeList = SOMMapData.uiMapTrainFtrTypeList;//new String[] {"Unmodified","Standardized (0->1 per ftr)","Normalized (vector mag==1)"};
			
	//	//GUI Objects	
	public final static int 
		uiTrainDataFrmtIDX	= 0,			//format that training data should take : unmodified, normalized or standardized
		uiTrainDatPartIDX	= 1,			//partition % of training data out of total data (rest is testing)
		uiMapRowsIDX 		= 2,            //map rows
		uiMapColsIDX		= 3,			//map cols
		uiMapEpochsIDX		= 4,			//# of training epochs
		uiMapShapeIDX		= 5,			//hexagonal or rectangular
		uiMapBndsIDX		= 6,			//planar or torroidal bounds
		uiMapKTypIDX		= 7,			//0 : dense cpu, 1 : dense gpu, 2 : sparse cpu.  dense needs appropriate lrn file format
		uiMapNHdFuncIDX		= 8,			//neighborhood : 0 : gaussian, 1 : bubble
		uiMapRadCoolIDX		= 9,			//radius cooling 0 : linear, 1 : exponential
		uiMapLrnCoolIDX		= 10,			//learning rate cooling 0 : linear 1 : exponential
		uiMapLrnStIDX		= 11,			//start learning rate
		uiMapLrnEndIDX		= 12,			//end learning rate
		uiMapRadStIDX		= 13,			//start radius
		uiMapRadEndIDX		= 14,			//end radius
		uiJPToDispIDX		= 15,			//which JP to display on map
		uiJPGToDispIDX		= 16,			//which group of jp's (a single jpg) to display on map
		uiMapNodeWtDispThreshIDX = 17,		//threshold for 
		uiMseRegionSensIDX	= 18;			//senstivity threshold for mouse-over, to determine membership to a particular jp (amount a query on the map per feature needs to be to be considered part of the JP that feature represents)
		
	public final int numGUIObjs = 19;	
	
	private double[] uiVals;				//raw values from ui components
	//source datapoints to be used to build files to send to SOM_MAP
	//public dataPoint[] straffTrainData, straffTestData, straffSmplData;
	//data format is from uiTrainDataFrmtIDX
	private int dataFrmtToUseToTrain;
	//threshold of wt value to display map node
	private float mapNodeWtDispThresh;
	//
	
	//////////////////////////////
	//map drawing 	draw/interaction variables
	public int[] dpFillClr, dpStkClr;
	public float[] SOM_mapDims;
	//public boolean saveMapImg;			//whether we should save the img of the map
	
	//TODO need to make these arrays?
	private PImage[] mapLocClrImg;//, mapRndClrImg;
	//which map is currently being shown
	private int curMapImgIDX;
	//scaling value - use this to decrease the image size and increase the scaling so it is rendered the same size
	public static final float mapScaleVal = 5.0f;

	
	public DispSOMMapExample mseOvrData;//label of mouse-over location in map
	
	private myPoint ULCrnr;	//upper left corner of map square - use to orient any drawn trajectories
	
	/////////
	//custom debug/function ui button names -empty will do nothing
	public String[] menuDbgBtnNames = new String[] {"Disp JPs","Disp Calc","Disp Ftrs","Disp Raw Data","Dbg 5"};//must have literals for every button or this is ignored by UI - buttons correspond to guiBtnNames list in mySideBarMenu 
	public String[] menuFuncBtnNames = new String[] {"Ld/proc CSV", "Ld/proc SQL", "Ld Train CSV", "Bld SOMDat", "Bld Ex Map"};//must have literals for every button or ignored
	
	public mySOMMapUIWin(SOM_StraffordMain _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed, String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt, _canDrawTraj);
		float stY = rectDim[1]+rectDim[3]-4*yOff,stYFlags = stY + 2*yOff;		
		trajFillClrCnst = SOM_StraffordMain.gui_DarkCyan;		//override this in the ctor of the instancing window class
		trajStrkClrCnst = SOM_StraffordMain.gui_Cyan;
		super.initThisWin(_canDrawTraj, true, false);
	}
	@Override
	//initialize all private-flag based UI buttons here - called by base class
	public void initAllPrivBtns(){
		truePrivFlagNames = new String[]{								//needs to be in order of flags
				"Merge w/Cur Preproc Dataset","Train W/Recs W/Event Data", "Building SOM", "Resetting Def Vals", "Loading Feature BMUs",
				"Using Scaled Ftrs For Dist Calc","Using ChiSq for Ftr Distance", "Unshared Ftrs are 0",	"Hide Train Data",
				"Hide Train Lbls",	"Hide Pop Map Nodes","Hide Map Nodes"//, "Showing Ftr Clr"
		};
		falsePrivFlagNames = new String[]{			//needs to be in order of flags
				"Build New Dataset From DB","Train W/All Recs","Build New Map ","Reset Def Vals","Not Loading Feature BMUs",
				"Using Unscaled Ftrs For Dist Calc","Not Using ChiSq Distance", "Ignoring Unshared Ftrs",	"Show Train Data",
				"Show Train Lbls",	"Show Pop Map Nodes","Show Map Nodes"//, "Not Showing Ftr Clr"
		};
		privModFlgIdxs = new int[]{
				appendTrainDataIDX,useOnlyEvntsToTrainIDX, buildSOMExe, resetMapDefsIDX, mapLoadFtrBMUsIDX,
				mapUseSclFtrDistIDX,mapUseChiSqDistIDX,mapSetSmFtrZeroIDX,mapDrawTrainDatIDX,
				mapDrawTrDatLblIDX,mapDrawMapNodesIDX,mapDrawAllMapNodesIDX};//,mapShowLocClrIDX};
		numClickBools = privModFlgIdxs.length;	
		//maybe have call for 		initPrivBtnRects(0);	
		initPrivBtnRects(0,numClickBools);
	}//initAllPrivBtns

	protected void initMe() {
		//initUIBox();				//set up ui click region to be in sidebar menu below menu's entries	
		float offset = 20;
		float width = rectDim[3]-(2*offset),//actually height, but want it square, and space is wider than high, so we use height as constraint - ends up being 834.8 x 834.8 with default screen dims
		xStart = rectDim[0] + .5f*(rectDim[2] - width);
		
		SOM_mapDims = new float[]{xStart, rectDim[1] + offset, width, width};
		SOM_Data = new SOMMapData(pa.th_exec,SOM_mapDims);
		//only set for visualization
		SOM_Data.win=this;
		
		//init specific sim flags
		initPrivFlags(numPrivFlags);			
		setPrivFlags(mapLoadFtrBMUsIDX,true);
		setPrivFlags(mapDrawTrainDatIDX,true);
		setPrivFlags(mapDrawMapNodesIDX,true);
		setPrivFlags(mapUseChiSqDistIDX,true);
		setPrivFlags(useOnlyEvntsToTrainIDX, true);
		dataFrmtToUseToTrain = (int)(this.guiObjs[uiTrainDataFrmtIDX].getVal());
		mapNodeWtDispThresh = (float)(this.guiObjs[uiMapNodeWtDispThreshIDX].getVal());

		//moved from SOM_Data ctor, to remove dependence on papplet in that object
		dpFillClr = pa.getClr(SOM_StraffordMain.gui_White);
		dpStkClr = pa.getClr(SOM_StraffordMain.gui_Blue);	
		initMapAras(1);
		ULCrnr = new myPoint(SOM_mapDims[0],SOM_mapDims[1],0);
		mseOvrData = null;	
				
	}//initMe
	
	protected void initMapAras(int numVals) {
		curMapImgIDX = 0;
		int w = (int) (SOM_mapDims[2]/mapScaleVal), h = (int) (SOM_mapDims[3]/mapScaleVal);
		mapLocClrImg = new PImage[numVals];
		for(int i=0;i<numVals;++i) {
			mapLocClrImg[i] = pa.createImage(w, h, pa.RGB);
		}
		
	}//initMapAras
	
	
	//set flag values when finished building map, to speed up initial display
	public void setFlagsDoneMapBuild(){
		setPrivFlags(mapDrawTrainDatIDX, false);
		setPrivFlags(mapDrawTrDatLblIDX, false);
		setPrivFlags(mapDrawMapNodesIDX, false);
		setPrivFlags(mapDrawAllMapNodesIDX, false);
	}
	
	private void setSOMDataDistType(int val) {if (SOM_Data != null) {SOM_Data.distType = val;}}
	
	@Override
	public void setPrivFlags(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		privFlags[flIDX] = (val ?  privFlags[flIDX] | mask : privFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case buildSOMExe 			: {break;}			//placeholder	
			case resetMapDefsIDX		: {if(val){resetUIVals(); setPrivFlags(resetMapDefsIDX,false);}}
			case mapDataLoadedIDX 		: {break;}			//placeholder				
			case mapLoadFtrBMUsIDX 		: {//whether or not to load the best matching units for each feature - this is a large construct so load only if necessary				
				break;}							
			case mapUseSclFtrDistIDX 	: {//whether or not to use the scaled (0-1) ftrs or the unscaled features for distance measures 
				//turn off chi sq flag if this is set
				//distance to use: 2 : scaled features, 1: chisq features or 0 : regular feature dists				
				if(val){
					setPrivFlags(mapUseChiSqDistIDX, false);
					setSOMDataDistType(2);
				} else {					setSOMDataDistType(0);			}
				break;}							
			case mapUseChiSqDistIDX		: {//whether or not to use chi-squared (weighted) distance for features
				//turn off scaled ftrs if this is set
				if(val){
					setPrivFlags(mapUseSclFtrDistIDX, false);
					setSOMDataDistType(1);
				} else {					setSOMDataDistType(0);			}
				break;}							
			case mapSetSmFtrZeroIDX		: {//whether or not distances between two datapoints assume that absent features in smaller-length datapoints are 0, or to ignore the values in the larger datapoints
				SOM_Data.setFlag(SOM_Data.mapSetSmFtrZeroIDX, val);
				break;}							
			case mapDrawTrainDatIDX		: {//draw training examples
				break;}							
			case mapDrawTrDatLblIDX 	: {//draw labels for training samples                                                       
				break;}
			case mapDrawMapNodesIDX		: {//draw map nodes
				break;}							
			case mapDrawAllMapNodesIDX	: {//draw all map nodes, even empty
				break;}							
//			case mapShowLocClrIDX		: {//draw all map nodes, even empty
//				break;}						
			case showSelRegionIDX		 : {//highlight a specific region of the map, either all nodes above a certain threshold for a chosen jp or jpgroup
				break;}
			case showSelJPIDX		 : {//if showSelRegionIDX == true, then this will show either a selected jp or jpgroup
				break;}

			case saveLocClrImgIDX : {break;}//save image
			case useOnlyEvntsToTrainIDX : {break;}//whether or not to limit training data set to only records that have specified jpgroups/jps from events, or to also use recs that only have specifications in prospect records
			case appendTrainDataIDX : {break;}//whether to merge new data pull with current existing preproced data(if any) or to build a new dataset from scratch. May have issues with duplicate records since events are not unique (multiple events on the same date have same event ID) VERIFY THIS ISNT" SQL BUG			
		}
	}//setFlag	
	

	//first verify that new .lrn file exists, then
	//build new SOM_MAP map using UI-entered values, then load resultant data
	protected void buildNewSOMMap(){
		pa.outStr2Scr("mySOMMapUIWin::buildNewSOMMap");
		int kVal = (int)this.guiObjs[uiMapKTypIDX].getVal();
		//verify sphere train/test data exists, otherwise save it
		if(!SOM_Data.mapCanBeTrained(kVal)){
			pa.outStr2Scr("No Training Data Found, unable to build SOM");
			setPrivFlags(buildSOMExe, false);
			return;
		}
		
		//build structures used to describe map from UI inputs
		HashMap<String, Integer> mapInts = new HashMap<String, Integer>(); 
		mapInts.put("mapCols", (int)this.guiObjs[uiMapColsIDX].getVal());
		mapInts.put("mapRows", (int)this.guiObjs[uiMapRowsIDX].getVal());
		mapInts.put("mapEpochs", (int)this.guiObjs[uiMapEpochsIDX].getVal());
		mapInts.put("mapKType", kVal);
		mapInts.put("mapStRad", (int)this.guiObjs[uiMapRadStIDX].getVal());
		mapInts.put("mapEndRad", (int)this.guiObjs[uiMapRadEndIDX].getVal());
		for (String key : mapInts.keySet()) {pa.outStr2Scr("mapInts["+key+"] = "+mapInts.get(key));}		
		HashMap<String, Float> mapFloats = new HashMap<String, Float>(); 
		mapFloats.put("mapStLrnRate",(float)this.guiObjs[uiMapLrnStIDX].getVal());
		mapFloats.put("mapEndLrnRate",(float)this.guiObjs[uiMapLrnEndIDX].getVal());
		for (String key : mapFloats.keySet()) {pa.outStr2Scr("mapFloats["+key+"] = "+ String.format("%.4f",mapFloats.get(key)));}		
		HashMap<String, String> mapStrings = new HashMap<String, String> ();
		mapStrings.put("mapGridShape", getUIListValStr(uiMapShapeIDX, (int)this.guiObjs[uiMapShapeIDX].getVal()));	
		mapStrings.put("mapBounds", getUIListValStr(uiMapBndsIDX, (int)this.guiObjs[uiMapBndsIDX].getVal()));	
		mapStrings.put("mapRadCool", getUIListValStr(uiMapRadCoolIDX, (int)this.guiObjs[uiMapRadCoolIDX].getVal()));	
		mapStrings.put("mapNHood", getUIListValStr(uiMapNHdFuncIDX, (int)this.guiObjs[uiMapNHdFuncIDX].getVal()));	
		mapStrings.put("mapLearnCool", getUIListValStr(uiMapLrnCoolIDX, (int)this.guiObjs[uiMapLrnCoolIDX].getVal()));	
		for (String key : mapStrings.keySet()) {pa.outStr2Scr("mapStrings["+key+"] = "+mapStrings.get(key));}
		
		//call map data object to build and execute map call
		SOM_Data.buildNewSOMMap(getPrivFlags(mapLoadFtrBMUsIDX),dataFrmtToUseToTrain, mapInts, mapFloats, mapStrings);

		setFlagsDoneMapBuild();
		pa.outStr2Scr("mySOMMapUIWin::buildNewSOMMap complete");
		setPrivFlags(buildSOMExe, false);
	}//buildNewSOMMap	
	
	
	//initialize structure to hold modifiable menu regions
	@Override
	protected void setupGUIObjsAras(){	
		//pa.outStr2Scr("setupGUIObjsAras in :"+ name);
//		if(numInstrs < 0){numInstrs = 0;}
		guiMinMaxModVals = new double [][]{  
			{0.0, 2.0, 1.0},			//uiTrainDataFrmtIDX
			{1.0, 100.0, 1.0},			//uiTrainDatPartIDX
			{1.0, 120.0, 10},			//uiMapRowsIDX 	 		
			{1.0, 120.0, 10},			//uiMapColsIDX	 		
			{1.0, 200.0, 10},			//uiMapEpochsIDX		
			{0.0, 1.0, 1},				//uiMapShapeIDX	 		
			{0.0, 1.0, 1},				//uiMapBndsIDX	 		
			{0.0, 2.0, .05},			//uiMapKTypIDX	 		
			{0.0, 1.0, 1},				//uiMapNHdFuncIDX		
			{0.0, 1.0, 1},				//uiMapRadCoolIDX		
			{0.0, 1.0, 1},				//uiMapLrnCoolIDX		
			{0.001, 1.0, 0.001},		//uiMapLrnStIDX	 		
			{0.001, 1.0, 0.001},		//uiMapLrnEndIDX		
			{2.0, 300.0, 1.0},			//uiMapRadStIDX	 	# nodes	
			{1.0, 10.0, 1.0},			//uiMapRadEndIDX		# nodes	
			{0.0, 260, 1.0},			//uiJPToDispIDX//which JP to display on map - idx into list of jps
			{0.0, 100, 1.0},				//uiJPGToDispIDX//which group of jp's (a single jpg) to display on map - idx into list of jps
			{0.0, 1.0, .01},			//uiMapNodeWtDispThreshIDX
			{0.0, 1.0, .1}				//uiMapRegionSensIDX

	
		};					
		guiStVals = new double[]{	
			1,		//uiTrainDataFrmtIDX
			90,		//uiTrainDatPartIDX
			10,		//uiMapRowsIDX 	 	
			10,		//uiMapColsIDX	 	
			10,		//uiMapEpochsIDX	
			0,		//uiMapShapeIDX	 	
			1,		//uiMapBndsIDX	 	
			2,		//uiMapKTypIDX	 	
			0,		//uiMapNHdFuncIDX	
			0,		//uiMapRadCoolIDX	
			0,		//uiMapLrnCoolIDX	
			0.1,	//uiMapLrnStIDX	 	
			0.01,	//uiMapLrnEndIDX	
			20.0,	//uiMapRadStIDX	 	
			1.0,	//uiMapRadEndIDX
			0,     //uiJPToDispIDX/
			0,      //uiJPGToDispIDX
			.04f,	//uiMapNodeWtDispThreshIDX
			0//uiMapRegionSensIDX
		};								//starting value
		uiVals = new double[numGUIObjs];//raw values
		System.arraycopy(guiStVals, 0, uiVals, 0, numGUIObjs);
		guiObjNames = new String[]{
				"Training Data Format",	//uiTrainDataFrmtIDX
				"Data % for Training",	//uiTrainDatPartIDX
				"# Map Rows",  			//uiMapRowsIDX 	 
				"# Map Columns",  		//uiMapColsIDX	 
				"# Training Epochs",  	//uiMapEpochsIDX
				"Map Node Shape",  		//uiMapShapeIDX	 
				"Map Boundaries",  		//uiMapBndsIDX	 
				"Dense/Sparse (C/G)PU", //uiMapKTypIDX	 
				"Neighborhood Func",  	//uiMapNHdFuncIDX
				"Radius Cooling", 		//uiMapRadCoolIDX
				"Learn rate Cooling",   //uiMapLrnCoolIDX
				"Start Learn Rate",  	//uiMapLrnStIDX	 
				"End Learn Rate",  		//uiMapLrnEndIDX
				"Start Cool Radius",  	//uiMapRadStIDX	 
				"End Cool Radius", 		//uiMapRadEndIDX	
				"Job Practice To Show", //uiJPToDispIDX/
				"JP Group To Show",     //uiJPGToDispIDX
				"Map Node Disp Wt Thresh",//uiMapNodeWtDispThreshIDX
				"Mouse Over JP Sensitivity"	//uiMapRegionSensIDX
				
		};			//name/label of component	
					
		//idx 0 is treat as int, idx 1 is obj has list vals, idx 2 is object gets sent to windows, 3 is object allows for lclick-up/rclick-down mod
		guiBoolVals = new boolean [][]{
			{true, true, true},								//uiTrainDataFrmtIDX
			{true, false, true},							//uiTrainDatPartIDX
			{true, false, true},      						//uiMapRowsIDX 	 	
			{true, false, true},      						//uiMapColsIDX	 	
			{true, false, true},      						//uiMapEpochsIDX	 	
			{true, true, true},      						//uiMapShapeIDX	 	
			{true, true, true},      						//uiMapBndsIDX	 	
			{true, true, true},      						//uiMapKTypIDX	 	
			{true, true, true},      						//uiMapNHdFuncIDX	
			{true, true, true},      						//uiMapRadCoolIDX	
			{true, true, true},      						//uiMapLrnCoolIDX	
			{false, false, true},      						//uiMapLrnStIDX	 	
			{false, false, true},      						//uiMapLrnEndIDX	 	
			{true, false, true},      						//uiMapRadStIDX	 	
			{true, false, true},      						//uiMapRadEndIDX
			{true, true, true},								//uiJPToDispIDX/
			{true, true, true},								//uiJPGToDispIDX	
			{false, false, true}, 							//uiMapNodeWtDispThreshIDX
			{false, false, true},							//uiMapRegionSensIDX
		};						//per-object  list of boolean flags
		
		//since horizontal row of UI comps, uiClkCoords[2] will be set in buildGUIObjs		
		guiObjs = new myGUIObj[numGUIObjs];			//list of modifiable gui objects
		if(numGUIObjs > 0){
			buildGUIObjs(guiObjNames,guiStVals,guiMinMaxModVals,guiBoolVals,new double[]{xOff,yOff});			//builds a horizontal list of UI comps
		}
	}
	public void resetUIVals(){
		for(int i=0; i<guiStVals.length;++i){	
			guiObjs[i].setVal(guiStVals[i]);
			//pa.outStr2Scr("i:"+i+" st obj val: " + guiStVals[i]);
		}
	}
	//if any ui values have a string behind them for display
	@Override
	public String getUIListValStr(int UIidx, int validx) {		
		//pa.outStr2Scr("UIidx : " + UIidx + "  Val : " + validx );
		switch(UIidx){//pa.score.staffs.size()
			case uiTrainDataFrmtIDX : {return uiMapTrainFtrTypeList[validx % uiMapTrainFtrTypeList.length];}
			case uiMapShapeIDX		: {return uiMapShapeList[validx % uiMapShapeList.length]; }
			case uiMapBndsIDX		: {return uiMapBndsList[validx % uiMapBndsList.length]; }
			case uiMapKTypIDX		: {return uiMapKTypList[validx % uiMapKTypList.length]; }
			case uiMapNHdFuncIDX	: {return uiMapNHoodList[validx % uiMapNHoodList.length]; }
			case uiMapRadCoolIDX	: {return uiMapRadClList[validx % uiMapRadClList.length]; }
			case uiMapLrnCoolIDX	: {return uiMapLrnClList[validx % uiMapLrnClList.length]; }	
			case uiJPToDispIDX		: {
				//refresh max size of guiobj
				guiObjs[uiJPToDispIDX].setNewMax(SOM_Data.jpByIdx.length-1);
				int idx = validx % SOM_Data.jpByIdx.length;
				return "" + SOM_Data.jpByIdx[idx] + " :(idx=" +idx+ ")"; 
			}	
			case uiJPGToDispIDX		: {
				//refresh max size of guiobj
				guiObjs[uiJPGToDispIDX].setNewMax(SOM_Data.jpgrpsByIdx.length-1);	
				int idx = validx % SOM_Data.jpgrpsByIdx.length;
				return "" + SOM_Data.jpgrpsByIdx[idx]; 
			}	
		}
		return "";
	}//getUIListValStr
	
	@Override
	protected void setUIWinVals(int UIidx) {
		double val = guiObjs[UIidx].getVal();
		if(uiVals[UIidx] != val){uiVals[UIidx] = val;} else {return;}//set values in raw array and only proceed if values have changed
		//int intVal = (int)val;
		switch(UIidx){
			case uiMapRowsIDX 	    : {guiObjs[uiMapRadStIDX].setVal(.5*Math.min(val, guiObjs[uiMapColsIDX].getVal()));break;}
			case uiMapColsIDX	    : {guiObjs[uiMapRadStIDX].setVal(.5*Math.min(guiObjs[uiMapRowsIDX].getVal(), val));break;}
			case uiMapEpochsIDX	    : {break;}
			case uiMapShapeIDX	    : {break;}
			case uiMapBndsIDX	    : {break;}
			case uiMapKTypIDX	    : {break;}
			case uiMapNHdFuncIDX	: {break;}
			case uiMapRadCoolIDX	: {break;}
			case uiMapLrnCoolIDX	: {break;}
			case uiMapLrnStIDX	    : {
				if(val <= guiObjs[uiMapLrnEndIDX].getVal()+guiMinMaxModVals[UIidx][2]) { guiObjs[UIidx].setVal(guiObjs[uiMapLrnEndIDX].getVal()+guiMinMaxModVals[UIidx][2]);}				
				break;}
			case uiMapLrnEndIDX	    : {
				if(val >= guiObjs[uiMapLrnStIDX].getVal()-guiMinMaxModVals[UIidx][2]) { guiObjs[UIidx].setVal(guiObjs[uiMapLrnStIDX].getVal()-guiMinMaxModVals[UIidx][2]);}				
				break;}
			case uiMapRadStIDX	    : {
				if(val <= guiObjs[uiMapRadEndIDX].getVal()+guiMinMaxModVals[UIidx][2]) { guiObjs[UIidx].setVal(guiObjs[uiMapRadEndIDX].getVal()+guiMinMaxModVals[UIidx][2]);}
				break;}
			case uiMapRadEndIDX	    : {
				if(val >= guiObjs[uiMapRadStIDX].getVal()-guiMinMaxModVals[UIidx][2]) { guiObjs[UIidx].setVal(guiObjs[uiMapRadStIDX].getVal()-guiMinMaxModVals[UIidx][2]);}
				break;}
			case uiJPToDispIDX : {//highlight display of different region of SOM map corresponding to selected JP
				curMapImgIDX = (int)val;
				pa.outStr2Scr("Setting UI JP Map to display to be idx :" + curMapImgIDX + " Corresponding to JP : " + SOM_Data.jpByIdx[curMapImgIDX] );				break;}
			case uiJPGToDispIDX : {//highlight display of different region of SOM map corresponding to group of JPs (jpg)
				break;}
			case uiMseRegionSensIDX : {
				break;}
			case uiMapNodeWtDispThreshIDX : {
				mapNodeWtDispThresh = (float)(this.guiObjs[uiMapNodeWtDispThreshIDX].getVal());
				break;}
			case uiTrainDataFrmtIDX : {//format of training data
				dataFrmtToUseToTrain = (int)(this.guiObjs[uiTrainDataFrmtIDX].getVal());
				break;}
		}
	}//setUIWinVals
	
	//get x and y locations relative to upper corner of map
	public float getSOMRelX (float x){return (x - SOM_mapDims[0]);}
	public float getSOMRelY (float y){return (y - SOM_mapDims[1]);}
	
	public float[] interpFloatAra(float[] a, float[] b, float t){
		if(a.length != b.length){pa.outStr2Scr("Error in interpFloatAra calc - sizes not equal."); return null;}
		float[] res = new float[a.length];
		for(int i=0;i<res.length;++i){res[i] = (a[i]*(1-t)) + (b[i]*t);}
		return res;		
	}
	
	private float[] interpFirstXFloatAraMult(float[] a, float[] b, float t, int x, float mult){
		if((a.length < x) || ( b.length < x)){pa.outStr2Scr("Error in interpFirstXFloatAra calc - arrays not big enough to return " + x + " values."); return null;}
		float[] res = new float[x];
		if (mult == 1.0){for(int i=0;i<res.length;++i){res[i] = (a[i]*(1-t)) + (b[i]*t);}} 
		else {			
			float mT = mult*t, m1t = mult * (1-t);
			for(int i=0;i<res.length;++i){res[i] = ((a[i]*m1t) + (b[i]*mT));}	
		}
		return res;		
	}
	//get treemap of features that interpolates between two maps of features
	private TreeMap<Integer, Float> interpTreeMap(TreeMap<Integer, Float> a, TreeMap<Integer, Float> b, float t, float mult){
		TreeMap<Integer, Float> res = new TreeMap<Integer, Float>();
		float Onemt = 1.0f-t;
		if(mult==1.0) {
			//first go through all a values
			for(Integer key : a.keySet()) {
				Float aVal = a.get(key), bVal = b.get(key);
				if(bVal == null) {bVal = 0.0f;}
				res.put(key, (aVal*Onemt) + (bVal*t));			
			}
			//next all b values
			for(Integer key : b.keySet()) {
				Float aVal = a.get(key);
				if(aVal == null) {aVal = 0.0f;} else {continue;}		//if aVal is not null then calced already
				Float bVal = b.get(key);
				res.put(key, (aVal*Onemt) + (bVal*t));			
			}
		} else {//scale by mult - precomputes color values
			float m1t = mult*Onemt, mt = mult*t;
			//first go through all a values
			for(Integer key : a.keySet()) {
				Float aVal = a.get(key), bVal = b.get(key);
				if(bVal == null) {bVal = 0.0f;}
				res.put(key, (aVal*m1t) + (bVal*mt));			
			}
			//next all b values
			for(Integer key : b.keySet()) {
				Float aVal = a.get(key);
				if(aVal == null) {aVal = 0.0f;} else {continue;}		//if aVal is not null then calced already
				Float bVal = b.get(key);
				res.put(key, (aVal*m1t) + (bVal*mt));			
			}			
		}		
		return res;
	}//interpolate between 2 tree maps
	

	//given pixel location relative to upper left corner of map, return map node
	//public float[] getMapNodeLocFromPxlLoc(float mapPxlX, float mapPxlY, float sclVal){	return new float[]{sclVal*SOM_Data.mapX * mapPxlX/SOM_mapDims[2], sclVal*SOM_Data.mapY * mapPxlY/SOM_mapDims[3]};}	
	public float[] getMapNodeLocFromPxlLoc(float mapPxlX, float mapPxlY, float sclVal){	return new float[]{sclVal* mapPxlX * SOM_Data.nodeXPerPxl, sclVal* mapPxlY * SOM_Data.nodeYPerPxl};}	
	//check whether the mouse is over a legitimate map location
	public boolean chkMouseOvr(int mouseX, int mouseY){		
		float mapMseX = getSOMRelX(mouseX), mapMseY = getSOMRelY(mouseY);//, mapLocX = mapX * mapMseX/mapDims[2],mapLocY = mapY * mapMseY/mapDims[3] ;
		if((mapMseX >= 0) && (mapMseY >= 0) && (mapMseX < SOM_mapDims[2]) && (mapMseY < SOM_mapDims[3])){
			float[] mapNLoc=getMapNodeLocFromPxlLoc(mapMseX,mapMseY, 1.0f);
			//SOM_Data.dispMessage("In Map : Mouse loc : " + mouseX + ","+mouseY+ "\tRel to upper corner ("+  mapMseX + ","+mapMseY +") | mapNLoc : ("+mapNLoc[0]+","+ mapNLoc[1]+")" );
			mseOvrData = getDataPointAtLoc(mapNLoc[0], mapNLoc[1], new myPointf(mapMseX, mapMseY,0));
//			int[] tmp = getDataClrAtLoc(mapNLoc[0], mapNLoc[1]);
//			SOM_Data.dispMessage("Color at mouse map loc :("+mapNLoc[0] + "," +mapNLoc[1]+") : " + tmp[0]+"|"+ tmp[1]+"|"+ tmp[2]);
			return true;
		} else {
			mseOvrData = null;
			return false;
		}
	}//chkMouseOvr
	
	//get datapoint at passed location in map coordinates (so should be in frame of map's upper right corner) - assume map is square and not hex
	public DispSOMMapExample getDataPointAtLoc(float x, float y, myPointf locPt){//, boolean useScFtrs){
		float sensitivity = (float) guiObjs[uiMseRegionSensIDX].getVal();
		int xInt = (int) Math.floor(x), yInt = (int) Math.floor(y), xIntp1 = (xInt+1)%SOM_Data.mapX, yIntp1 = (yInt+1)%SOM_Data.mapY;		//assume torroidal map		
		//need to divide by width/height of map * # cols/rows to get mapped to actual map nodes
		//dispMessage("In getDataPointAtLoc : Mouse loc in Nodes : " + x + ","+y+ "\txInt : "+ xInt + " yInt : " + yInt );
		float xInterp = x - xInt, yInterp = y - yInt;//, xIsq = xInterp*xInterp, yIsq = yInterp*yInterp,oneMxIsq = (1-xInterp)*(1-xInterp),oneMyIsq = (1-yInterp)*(1-yInterp);
		//always compare standardized feature data in test/train data to standardized feature data in map
		TreeMap<Integer, Float> LowXLowYFtrs = SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)).getCurrentFtrMap(SOMMapData.useScaledDat), LowXHiYFtrs= SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)).getCurrentFtrMap(SOMMapData.useScaledDat),
				 HiXLowYFtrs= SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)).getCurrentFtrMap(SOMMapData.useScaledDat),  HiXHiYFtrs= SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1)).getCurrentFtrMap(SOMMapData.useScaledDat);
		
//		TreeMap<Integer, Float> LowXLowYFtrs = SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)).getCurrentFtrMap(dataFrmtToUseToTrain), LowXHiYFtrs= SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)).getCurrentFtrMap(dataFrmtToUseToTrain),
//				 HiXLowYFtrs= SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)).getCurrentFtrMap(dataFrmtToUseToTrain),  HiXHiYFtrs= SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1)).getCurrentFtrMap(dataFrmtToUseToTrain);
		try{
			TreeMap<Integer, Float> ftrs = interpTreeMap(interpTreeMap(LowXLowYFtrs, LowXHiYFtrs,yInterp,1.0f),interpTreeMap(HiXLowYFtrs, HiXHiYFtrs,yInterp,1.0f),xInterp,255.0f);	
			DispSOMMapExample dp = SOM_Data.buildTmpDataExample(locPt, ftrs, sensitivity);
			//dp.setCorrectScaling();
			//dp.setLabel(res);
			dp.setMapLoc(locPt);		
			return dp;
		} catch (Exception e){
			pa.outStr2Scr("Exception triggered in mySOMMapUIWin::getDataPointAtLoc : \n"+e.toString() + "\n\tMessage : "+e.getMessage() );
			return null;
		}
	}//getDataPointAtLoc	
	
	//return a ftrMap at a location
	public TreeMap<Integer, Float> getDataMapAtLoc(float x, float y){
		int xInt = (int) Math.floor(x), yInt = (int) Math.floor(y), xIntp1 = (xInt+1)%SOM_Data.mapX, yIntp1 = (yInt+1)%SOM_Data.mapY;		//assume torroidal map		
		//dispMessage("In getDataClrAtLoc :  loc in Nodes : " + x + ","+y+ "\txInt : "+ xInt + " yInt : " + yInt );
		float xInterp = x - xInt, yInterp = y - yInt;		
		//always compare standardized feature data in test/train data to standardized feature data in map
		TreeMap<Integer, Float> LowXLowYFtrs = SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)).getCurrentFtrMap(SOMMapData.useScaledDat), LowXHiYFtrs= SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)).getCurrentFtrMap(SOMMapData.useScaledDat),
				 HiXLowYFtrs= SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)).getCurrentFtrMap(SOMMapData.useScaledDat),  HiXHiYFtrs= SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1)).getCurrentFtrMap(SOMMapData.useScaledDat);
//		TreeMap<Integer, Float> LowXLowYFtrs = SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)).getCurrentFtrMap(dataFrmtToUseToTrain), LowXHiYFtrs= SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)).getCurrentFtrMap(dataFrmtToUseToTrain),
//				 HiXLowYFtrs= SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)).getCurrentFtrMap(dataFrmtToUseToTrain),  HiXHiYFtrs= SOM_Data.MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1)).getCurrentFtrMap(dataFrmtToUseToTrain);
		try{
			TreeMap<Integer, Float> ftrs = interpTreeMap(interpTreeMap(LowXLowYFtrs, LowXHiYFtrs,yInterp,1.0f),interpTreeMap(HiXLowYFtrs, HiXHiYFtrs,yInterp,1.0f),xInterp,255.0f);	
			return ftrs;//(((int)ftrs[0] & 0xff) << 16) + (((int)ftrs[1] & 0xff) << 8) + ((int)ftrs[2] & 0xff);			
		} catch (Exception e){
			pa.outStr2Scr("Error in getDataClrAtLoc at location : (" + x + ", " + y + ") :\n " + e.toString() + "\t\n Message : "+ e.getMessage());
			return new TreeMap<Integer, Float>();// 0xFFFFFFFF;
		}
	}//getDataClrAtLoc
		
	//make color based on ftr value at particular index
	//jpIDX is index in feature vector we are querying
	//call this if map is trained on unmodified data
	private int getDataClrFromFtrVecUnModded(TreeMap<Integer, Float> ftrMap, Integer jpIDX) {		
		Float ftrVal = ftrMap.get(jpIDX);
		int ftr = 0;
		if(ftrVal != null) {
			ftr = SOM_Data.scaleUnfrmttedFtrData(ftrVal, jpIDX); 
		}
		int clrVal = ((ftr & 0xff) << 16) + ((ftr & 0xff) << 8) + (ftr & 0xff);
		return clrVal;
	}//getDataClrFromFtrVec
	
	//make color based on ftr value at particular index
	//jpIDX is index in feature vector we are querying
	//call this if map is trained on scaled or normed ftr data
	private int getDataClrFromFtrVec(TreeMap<Integer, Float> ftrMap, Integer jpIDX) {
		Float ftrVal = ftrMap.get(jpIDX);
//		if(ftrVal == null) {	ftrVal=0.0f;		}
//		if (minFtrValSeen[jpIDX] > ftrVal) {minFtrValSeen[jpIDX]=ftrVal;}
//		else if (maxFtrValSeen[jpIDX] < ftrVal) {maxFtrValSeen[jpIDX]=ftrVal;}
		int ftr = 0;
		if(ftrVal != null) {	ftr = Math.round(ftrVal);		}
		int clrVal = ((ftr & 0xff) << 16) + ((ftr & 0xff) << 8) + (ftr & 0xff);
		return clrVal;
	}//getDataClrFromFtrVec
	
	@Override
	public void initDrwnTrajIndiv(){}
	@Override
	public void drawCustMenuObjs(){}
	@Override
	protected boolean simMe(float modAmtSec) {return false;}
	//set camera to custom location - only used if dispFlag set
	@Override
	protected void setCameraIndiv(float[] camVals){}
	@Override
	protected void stopMe() {}	
	@Override
	protected void drawMe(float animTimeMod) {
		setPrivFlags(mapDataLoadedIDX,SOM_Data.isMapDrawable());
		if(getPrivFlags(mapDataLoadedIDX)){ drawMap();}	
		if(getPrivFlags(buildSOMExe)){buildNewSOMMap();}
	}
	
	//map node fill and stroke color
	private final int[] mapNodeClr = new int[] {255, 0,255,255};
	public void drawMap(){
		//draw map rectangle
		pa.pushMatrix();pa.pushStyle();
		pa.noLights();
		pa.scale(mapScaleVal);
		pa.image(mapLocClrImg[curMapImgIDX],SOM_mapDims[0]/mapScaleVal,SOM_mapDims[1]/mapScaleVal); if(getPrivFlags(saveLocClrImgIDX)){mapLocClrImg[curMapImgIDX].save(SOM_Data.getSOMLocClrImgForJPFName(curMapImgIDX));  setPrivFlags(saveLocClrImgIDX,false);}
		pa.lights();
		//pa.setColorValFill(SOM_SphereMain.gui_OffWhite);
		//pa.rect(mapDims);//TODO replace with a texture
		pa.popStyle();pa.popMatrix();
		
		pa.pushMatrix();pa.pushStyle();
		pa.translate(SOM_mapDims[0],SOM_mapDims[1],0);
		//draw nodes
		pa.pushMatrix();pa.pushStyle();
		pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
		if(mseOvrData != null){mseOvrData.drawMeLblMap(pa,mseOvrData.label,true);}
		if(getPrivFlags(mapDrawTrainDatIDX)){		SOM_Data.drawTrainData(pa, curMapImgIDX, getPrivFlags(mapDrawTrDatLblIDX));}			
		pa.popStyle();pa.popMatrix();
		//draw map nodes, either with or without empty nodes
		if(getPrivFlags(mapDrawAllMapNodesIDX)){	SOM_Data.drawAllNodes( pa,  mapNodeClr, mapNodeClr);		} 
		if(getPrivFlags(mapDrawMapNodesIDX)){		SOM_Data.drawNodesWithWt(pa, mapNodeWtDispThresh, curMapImgIDX, mapNodeClr, mapNodeClr);}//SOM_Data.drawExMapNodes( pa, curMapImgIDX, mapNodeClr, mapNodeClr);		} 
		pa.popStyle();pa.popMatrix();
	}//drawMap()		
	
	
	//sets colors of background image of map
	public void setMapImgClrs(){ //mapRndClrImg
		float[] c;		
		for (int i=0;i<mapLocClrImg.length;++i) {	mapLocClrImg[i].loadPixels();}
		for(int y = 0; y<mapLocClrImg[0].height; ++y){
			int yCol = y * mapLocClrImg[0].width;
			for(int x = 0; x < mapLocClrImg[0].width; ++x){
				c = getMapNodeLocFromPxlLoc(x, y,mapScaleVal);
				TreeMap<Integer, Float> ftrs = getDataMapAtLoc(c[0],c[1]);
				//for (int i=0;i<mapLocClrImg.length;++i) {	mapLocClrImg[i].pixels[x+yCol] = getDataClrFromFtrVec(ftrs, i);}
				for (Integer jp : ftrs.keySet()) {mapLocClrImg[jp].pixels[x+yCol] = getDataClrFromFtrVec(ftrs, jp);}
			}
		}
		for (int i=0;i<mapLocClrImg.length;++i) {	mapLocClrImg[i].updatePixels();		}
	}//setMapImgClrs
		
	//return strings for directory names and for individual file names that describe the data being saved.  used for screenshots, and potentially other file saving
	//first index is directory suffix - should have identifying tags based on major/archtypical component of sim run
	//2nd index is file name, should have parameters encoded
	@Override
	protected String[] getSaveFileDirNamesPriv() {
		String dirString="", fileString ="";
		//for(int i=0;i<uiAbbrevList.length;++i) {fileString += uiAbbrevList[i]+"_"+ (uiVals[i] > 1 ? ((int)uiVals[i]) : uiVals[i] < .0001 ? String.format("%6.3e", uiVals[i]) : String.format("%3.3f", uiVals[i]))+"_";}
		return new String[]{dirString,fileString};	
	}

	//custom functions launched by UI input
	//if launching threads for custom functions, need to remove clearFuncBtnState call in function below and call clearFuncBtnState when thread ends
	private void custFunc0(){
		//load data from raw local csvs of db download
		SOM_Data.loadAllRawData(true, getPrivFlags(useOnlyEvntsToTrainIDX), getPrivFlags(appendTrainDataIDX));
		clearFuncBtnState(0,false);
	}			

	private void custFunc1(){
		//load data via sql connection
		SOM_Data.loadAllRawData(false, getPrivFlags(useOnlyEvntsToTrainIDX), getPrivFlags(appendTrainDataIDX));
		clearFuncBtnState(1,false);
	}	
	
	private void custFunc2(){	
		//load data from preprocessed local csv files
		SOM_Data.loadAllPropsectMapData(getPrivFlags(useOnlyEvntsToTrainIDX));
		clearFuncBtnState(2,false);
	}			
	private void custFunc3(){	
		SOM_Data.buildAndSaveTrainingData(dataFrmtToUseToTrain, (float)(.01*this.guiObjs[uiTrainDatPartIDX].getVal()));
		clearFuncBtnState(3,false);
	}			
	private void custFunc4(){	
		//load a pre-built map and render it - map needs to coincide with the data currently in memory
		SOM_Data.dbgBuildExistingMap();
		//custom function code here
		clearFuncBtnState(4,false);
	}		
	@Override
	public void clickFunction(int btnNum) {
		pa.outStr2Scr("click cust function in "+name+" : btn : " + btnNum);
		switch(btnNum){
			case 0 : {	custFunc0();	break;}
			case 1 : {	custFunc1();	break;}
			case 2 : {	custFunc2();	break;}
			case 3 : {	custFunc3();	break;}
			case 4 : {	custFunc4();	break;}
			default : {break;}
		}	
	}		//only for display windows
	
	private void toggleDbgBtn(int idx, boolean val) {
		setPrivFlags(idx, !getPrivFlags(idx));
	}
	
	//debug function
	//if launching threads for debugging, need to remove clearDBGState call in function below and call clearDBGState when thread ends
	private void dbgFunc0() {	
		SOM_Data.dbgShowUniqueJPsSeen();
		clearDBGBtnState(0,false);
	}	
	private void dbgFunc1(){
		SOM_Data.dbgShowCalcEqs();
		clearDBGBtnState(1,false);
	}	
	private void dbgFunc2(){
		SOM_Data.dbgShowAllFtrVecs();
		clearDBGBtnState(2,false);
	}	
	private void dbgFunc3(){	
		SOM_Data.dbgShowAllRawData();
		clearDBGBtnState(3,false);
	}	
	private void dbgFunc4(){	
		clearDBGBtnState(4,false);
	}	
	private void dbgFunc5(){	
		clearDBGBtnState(5,false);
	}	

	@Override
	public void clickDebug(int btnNum){
		pa.outStr2Scr("click debug in "+name+" : btn : " + btnNum);
		switch(btnNum){
			case 0 : {	dbgFunc0();	break;}//verify priority queue functionality
			case 1 : {	dbgFunc1();	break;}//verify FEL pq integrity
			case 2 : {	dbgFunc2();	break;}
			case 3 : {	dbgFunc3();	break;}
			case 4 : {	dbgFunc4();	break;}
			case 5 : {	dbgFunc5();	break;}
			default : {break;}
		}		
	}//clickDebug
	//handle mouseover 
	@Override
	protected boolean hndlMouseMoveIndiv(int mouseX, int mouseY, myPoint mseClckInWorld){
		boolean res = false;
		if(getPrivFlags(mapDataLoadedIDX)){ res = chkMouseOvr(mouseX, mouseY);	}
		return res;
	}	
	@Override
	protected boolean hndlMouseClickIndiv(int mouseX, int mouseY, myPoint mseClckInWorld, int mseBtn) {
		boolean mod = false;
		
		
		if(mod) {return mod;}
		else {return checkUIButtons(mouseX, mouseY);}
	}
	@Override
	protected boolean hndlMouseDragIndiv(int mouseX, int mouseY,int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld, int mseBtn) {
		boolean mod = false;
		
		return mod;
	}
	@Override
	protected void hndlMouseRelIndiv() {	}	
	@Override
	public void hndlFileLoadIndiv(String[] vals, int[] stIdx) {
		
	}

	@Override
	public ArrayList<String> hndlFileSaveIndiv() {
		ArrayList<String> res = new ArrayList<String>();

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
	@Override
	protected void drawRightSideInfoBar(float modAmtMillis) {}
	//resize drawn all trajectories
	@Override
	protected void resizeMe(float scale) {		
		//any resizing done
	}
	@Override
	protected void closeMe() {}
	@Override
	protected void showMe() {
		pa.setMenuDbgBtnNames(menuDbgBtnNames);
		pa.setMenuFuncBtnNames(menuFuncBtnNames);		
	}
	@Override
	public String toString(){
		String res = super.toString();
		return res;
	}


}//myTrajEditWin
