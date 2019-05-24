package strafford_SOM_PKG;

import java.io.File;
import java.util.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_ui.*;
import base_SOM_Objects.som_utils.SOMProjConfigData;
import base_UI_Objects.*;
import base_Utils_Objects.*;
import processing.core.PImage;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
import strafford_SOM_PKG.straff_Utils.*;
import strafford_SOM_PKG.straff_Utils.featureCalc.StraffWeightCalc;


//window that accepts trajectory editing
public class Straff_SOMMapUIWin extends SOMMapUIWin {
	
	//idxs of boolean values/flags - instance-specific
	public static final int 
		custExCalcedIDX				= numSOMBasePrivFlags + 0,			//whether customer prospect examples have been loaded and ftrs have been calculated or not
		tpExCalcedIDX				= numSOMBasePrivFlags + 1,			//whether true propsect examples have been loaded and ftrs have been calculated or not
		mapDrawPrdctNodesIDX 		= numSOMBasePrivFlags + 2,
		mapDrawCurProdZoneIDX	 	= numSOMBasePrivFlags + 3,			//show currently selected prod jps' products and influence zones
		//display/interaction
		mapDrawTruePspctIDX			= numSOMBasePrivFlags + 4,			//draw true prospect examples on map		
		mapDrawCustAnalysisVisIDX	= numSOMBasePrivFlags + 5,			//whether or not to draw feature calc analysis graphs for customer examples
		mapDrawTPAnalysisVisIDX		= numSOMBasePrivFlags + 6,			//whether or not to draw feature calc analysis graphs for true prospect examples
		mapDrawCalcFtrOrAllVisIDX	= numSOMBasePrivFlags + 7,			//whether to draw calc obj for ftr-related jps, or all jps present		
		
		showSelJPIDX				= numSOMBasePrivFlags + 8, 			//if showSelRegionIDX == true, then this will show either a selected jp or jpgroup
		//train/test data managemen
		procTruProspectsIDX			= numSOMBasePrivFlags + 9,			//this will process true prospects, and load them if they haven't been loaded
		saveProdMapsOfPrspctsIDX	= numSOMBasePrivFlags + 10;			//this will save all the product data for the currently selected prod JP

	public static final int numPrivFlags = numSOMBasePrivFlags + 11;
	
	//SOM map list options
	public String[] 
		uiRawDataSourceList = new String[] {"Prebuilt CSV Files","Data Tables Via SQL"};
	
	//	//GUI Objects	
	public final static int //offset from end of base class SOM UI objs
		uiRawDataSourceIDX 			= numSOMBaseGUIObjs + 0,			//source of raw data to be preprocced and used to train the map
		uiFtrJPGToDispIDX			= numSOMBaseGUIObjs + 1,			//which group of jp's (a single jpg) to display on map
		uiFtrJPToDispIDX			= numSOMBaseGUIObjs + 2,			//which JP Ftr IDX to display as map
		//uiAllJpgSeenToDispIDX		= numSOMBaseGUIObjs + 3,			//display products of this jpg
		uiAllJpSeenToDispIDX		= numSOMBaseGUIObjs + 3,			//display products with this jp
		uiProdZoneDistThreshIDX		= numSOMBaseGUIObjs + 4;			//max distance from a product that a map node should be considered to be covered by that product
	
	public final int numGUIObjs = numSOMBaseGUIObjs + 5;
	
	//types of data that can be used for calc analysis 
	//private int[] calcAnalysisTypes = new int[] {StraffSOMMapManager.jps_FtrIDX,StraffSOMMapManager.jps_AllIDX};
	private int curCalcAnalysisTypeIDX = Straff_SOMMapManager.jps_AllIDX;
	private int curCalcAnalysisJPTypeIDX = Straff_SOMMapManager.jps_AllIDX;
	
	//raw data source : 0 == csv, 1 == sql
	private int rawDataSource;
	//max sq_distance to display map nodes as under influence/influencing certain products
	private double prodZoneDistThresh;
	//////////////////////////////
	//map drawing 	draw/interaction variables
	//start location of SOM image - stX, stY, and dimensions of SOM image - width, height; locations to put calc analysis visualizations
	public float[] calcAnalysisLocs;	
	//to draw analysis results
	private float calcScale = .5f;
	//set analysisHt equal to be around 1/2 SOM_mapDims height
	private float analysisHt, analysisAllJPBarWidth, analysisPerJPWidth;
	//other PImage maps set in base class
	//array of per jpg map wts - equally weighted all jps within jpg
	private PImage[] mapPerJpgWtImgs;
	
	//which product is being shown by single-product display visualizations, as index in list of jps of products
	private int curProdToShowIDX;
	//which jp is currently being investigated of -all- jps
	private int curAllJPToShowIDX;
		
	/////////
	//custom debug/function ui button names -empty will do nothing
	public String[][] menuBtnNames = new String[][] {	//each must have literals for every button defined in side bar menu, or ignored
		{"Load All Raw ---", "Func 01","Recalc Features"},	//row 1
		{"Train data","Prspcts", "SOM Cfg", "All->Bld Map", "Prblt Map"},	//row 2
		{"Raw","Proced","JpJpg","MapDat","Dbg 5"}	
	};

	//used to switch button name for 1st button to reflect whether performing csv-based load of raw data or sql query
	private String[] menuLdRawFuncBtnNames = new String[] {"CSV", "SQL"};
	
	
	public Straff_SOMMapUIWin(my_procApplet _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed, String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt, _canDrawTraj);
		float stY = rectDim[1]+rectDim[3]-4*yOff,stYFlags = stY + 2*yOff;		
		trajFillClrCnst = my_procApplet.gui_DarkCyan;	
		trajStrkClrCnst = my_procApplet.gui_Cyan;
		super.initThisWin(_canDrawTraj, true, false);
	}//ctor

	//build arrays from passed strings and idxs built in base window, appends to them with any buttons specific to this application
	protected void initAllSOMPrivBtns_Indiv(String[] _baseTrueNames, String[] _baseFalseNames, int[] _baseFlags) {
		String[] tmpTruePrivFlagNames = new String[]{								//needs to be in order of flags
				//"Train W/Recs W/Event Data", 				
				"Hide Tru Prspct",
				"Hide Products","Hide Cur Prod Zone",
				"Calc Plot on Ftr JPs","Hide Cust Calc Plot", "Hide Tru Prspct Calc Plot", 
				"Map Tru Prspct BMUs",	"Saving Prospect Mappings for prods"
		};
		String[] tmpFalsePrivFlagNames = new String[]{			//needs to be in order of flags
				//"Train W/All Recs",				
				"Show Tru Prspct",
				"Show Products","Show Cur Prod Zone",
				"Calc Plot on All JPs", "Show Cust Calc Plot", "Show Tru Prspct Calc Plot", 
				"Map Tru Prspct BMUs",	"Save Prospect Mappings for prods"
		};
		int[] tmpPrivModFlgIdxs = new int[]{
				//useOnlyEvntsToTrainIDX, 				
				mapDrawTruePspctIDX,
				mapDrawPrdctNodesIDX,mapDrawCurProdZoneIDX,
				mapDrawCalcFtrOrAllVisIDX, mapDrawCustAnalysisVisIDX,mapDrawTPAnalysisVisIDX,
				procTruProspectsIDX,saveProdMapsOfPrspctsIDX
		};
		initAllPrivBtns_Final(tmpTruePrivFlagNames,  tmpFalsePrivFlagNames, tmpPrivModFlgIdxs ,_baseTrueNames, _baseFalseNames, _baseFlags);
	}//initAllSOMPrivBtns_Indiv

	@Override
	protected void initMeIndiv() {
		//based on width of map
		analysisHt = (SOM_mapDims[1]*.45f);
		//for single jp detail display
		analysisPerJPWidth = (SOM_mapDims[0]*.1f);
		setPrivFlags(mapDrawCalcFtrOrAllVisIDX, true);
		//dataFrmtToUseToTrain = (int)(this.guiObjs[uiTrainDataFrmtIDX].getVal());
		prodZoneDistThresh = this.guiObjs[uiProdZoneDistThreshIDX].getVal();
		rawDataSource = (int)(this.guiObjs[uiRawDataSourceIDX].getVal());

		//moved from mapMgr ctor, to remove dependence on papplet in that object
		pa.setAllMenuBtnNames(menuBtnNames);	
		initMapAras(1, 1);
	}//initMeIndiv()
	
	@Override
	protected void initAllPrivFlags() {	initPrivFlags(numPrivFlags);}
	
	@Override
	//SOM_mapDims is built by base class initMe
	protected SOMMapManager buildMapMgr() {
		//SOM_mapDims : start x and y and dimensions of full map visualization as function of visible window size;
		//including strings for default directories specific to current project setup and Strafford
		TreeMap<String, Object> _argsMap = new TreeMap<String,Object>();
		//provide default values used by program
		_argsMap.put("configDir", "StraffordProject" + File.separator+"config" + File.separator);
		_argsMap.put("dataDir", "StraffordProject" + File.separator);
		_argsMap.put("logLevel",0);//0 is console alone,1 is log file alone, 2 is both
		
		return new Straff_SOMMapManager(SOM_mapDims, _argsMap);
	}
	
	@Override
	protected void setVisScreenDimsPriv_Indiv() {
		//now build calc analysis offset struct
		calcAnalysisLocs = new float[] {(SOM_mapLoc[0]+SOM_mapDims[0])*calcScale + xOff,(SOM_mapLoc[1]+SOM_mapDims[1])*calcScale + 10.0f};
		setAnalysisDimWidth();		
	}
	
	//per jp bar width ~= total width / # of jps
	protected void setAnalysisDimWidth() {
		analysisAllJPBarWidth = (curVisScrDims[0]/(1.0f+((Straff_SOMMapManager) mapMgr).numFtrsToShowForCalcAnalysis(curCalcAnalysisJPTypeIDX)))*.98f;	
	}
	
	//called from SOMMapUIWin base on initMapAras - this instances 2ndary maps/other instance-specific maps
	@Override
	protected void initMapArasIndiv(int w, int h, int format, int num2ndryMaps) {
		curAllJPToShowIDX = 0;
		mapPerJpgWtImgs = new PImage[num2ndryMaps];
		for(int i=0;i<mapPerJpgWtImgs.length;++i) {
			mapPerJpgWtImgs[i] = pa.createImage(w, h, format);
		}	
	}//instance-specific init
		
	@Override
	protected void setPrivFlagsIndiv(int idx, boolean val) {
		switch (idx) {//special actions for each flag
			case mapDrawTruePspctIDX	: {//draw true prospect examples
				break;}		
			case mapDrawCalcFtrOrAllVisIDX : {
				curCalcAnalysisJPTypeIDX = (val ? Straff_SOMMapManager.jps_FtrIDX : Straff_SOMMapManager.jps_AllIDX);		
				setAnalysisDimWidth();
				break;}
			case mapDrawCustAnalysisVisIDX	: {//whether or not to draw feature calc analysis graphs  
				if (val) {//if setting to true then aggregate data
					setPrivFlags(mapDrawTPAnalysisVisIDX, false);
					curCalcAnalysisTypeIDX= StraffWeightCalc.custCalcObjIDX;
					((Straff_SOMMapManager) mapMgr).processCalcAnalysis(curCalcAnalysisTypeIDX);	
					setAnalysisDimWidth();
				} else {
					
				}
				break;}
			case mapDrawTPAnalysisVisIDX	: {//whether or not to draw feature calc analysis graphs  
				if (val) {//if setting to true then aggregate data
					setPrivFlags(mapDrawCustAnalysisVisIDX, false);
					curCalcAnalysisTypeIDX= StraffWeightCalc.tpCalcObjIDX;
					((Straff_SOMMapManager) mapMgr).processCalcAnalysis(curCalcAnalysisTypeIDX);	
					setAnalysisDimWidth();
				} else {
					
				}
				break;}
			case showSelJPIDX		 : {//if showSelRegionIDX == true, then this will show either a selected jp or jpgroup
				break;}
			case procTruProspectsIDX : {
//				if(val) {
//					((StraffSOMMapManager) mapMgr).buildAndSaveTrueProspectReport();	
//					addPrivBtnToClear(procTruProspectsIDX);					
//				}			
				break;}		//put true prospects on the SOM
			case saveProdMapsOfPrspctsIDX : {
				if(val) {
					((Straff_SOMMapManager) mapMgr).saveAllExamplesToSOMMappings(prodZoneDistThresh, true, true);		
					addPrivBtnToClear(saveProdMapsOfPrspctsIDX);			
				}				
				break;}		//save all product to prospect mappings given currently selected product jp and dist thresh
			
			case custExCalcedIDX : {			break;}			
			case tpExCalcedIDX  : {			break;}			
		}		
	}//setPrivFlagsIndiv
		
	@Override
	//called by base SOM Window class, passing arrays with default values already pre-set
	protected void setupGUIObjsArasIndiv(double [][] _baseGuiMinMaxModVals, double[] _baseGuiStVals, String[] _baseGuiObjNames, boolean [][] _baseGuiBoolVals) {
		double [][] _tmpGuiMinMaxModVals = new double [][]{  
			{0.0, uiRawDataSourceList.length-1, 1},			//uiRawDataSourceIDX
			{0.0, 100, 1.0},			//uiFtrJPGToDispIDX		
			{0.0, 260, 1.0},			//uiFtrJPToDispIDX	
//		    {0.0, 100, 1.0},			//uiAllJpgSeenToDispIDX	
			{0.0, 260, 1.0},			//uiAllJpSeenToDispIDX	
			{0.0, 2, .01},				//uiProdZoneDistThreshIDX	
		};					
		double[] _tmpGuiStVals = new double[]{	
			0,		//uiRawDataSourceIDX
			0,      //uiFtrJPGToDispIDX		
			0,     	//uiFtrJPToDispIDX	
//			0,		//uiAllJpgSeenToDispIDX	
			0,     	//uiAllJpSeenToDispIDX	
			0.99,	//uiProdZoneDistThreshIDX
		};								//starting value
		String[] _tmpGuiObjNames = new String[]{
			"Raw Data Source", 			//uiRawDataSourceIDX
			"JPGrp Ftrs Shown",     	//uiFtrJPGToDispIDX		
			"JP Ftr Shown", 			//uiFtrJPToDispIDX		
//			"All JPGrp to Show",   		//uiAllJpgSeenToDispIDX	
			"All JP to Show",   		//uiAllJpSeenToDispIDX	
			"Prod Max Sq Dist",			//uiProdZoneDistThreshIDX			
		};			//name/label of component	
					
		//idx 0 is treat as int, idx 1 is obj has list vals, idx 2 is object gets sent to windows, 3 is object allows for lclick-up/rclick-down mod
		boolean [][] _tmpGuiBoolVals = new boolean [][]{
			{true, true, true},			//uiRawDataSourceIDX
			{true, true, true},			//uiFtrJPGToDispIDX		
			{true, true, true},			//uiFtrJPToDispIDX		
//			{true, true, true},			//uiAllJpgSeenToDispIDX	
			{true, true, true},			//uiAllJpSeenToDispIDX	
			{false, false, true}, 		//uiProdZoneDistThreshIDX
		};						//per-object  list of boolean flags
		
		setupGUIObjsArasFinal(numGUIObjs,
				_tmpGuiMinMaxModVals,_tmpGuiStVals,_tmpGuiObjNames,_tmpGuiBoolVals,
				_baseGuiMinMaxModVals, _baseGuiStVals, _baseGuiObjNames, _baseGuiBoolVals);
	};

	@Override
	protected String getUIListValStrIndiv(int UIidx, int validx) {
		//msgObj.dispMessage("mySOMMapUIWin","getUIListValStr","UIidx : " + UIidx + "  Val : " + validx );
		switch(UIidx){//pa.score.staffs.size()
			case uiRawDataSourceIDX 			: {return uiRawDataSourceList[validx % uiRawDataSourceList.length];}
			case uiFtrJPGToDispIDX				: {return ((Straff_SOMMapManager) mapMgr).getFtrJpGrpStrByIdx(validx); 	}	
			case uiFtrJPToDispIDX				: {return ((Straff_SOMMapManager) mapMgr).getFtrJpStrByIdx(validx); 	}	
			//case uiAllJpgSeenToDispIDX			: {return ((StraffSOMMapManager) mapMgr).getAllJpGrpByIdxStr(validx); 		}	
			case uiAllJpSeenToDispIDX			: {return ((Straff_SOMMapManager) mapMgr).getAllJpStrByIdx(validx); 		}	
		}
		return "";
	}//getUIListValStrIndiv

	@Override
	//get index of list for this instance's lists (not found in base window
	protected int getIdxFromListStringIndiv(int UIidx, String dat){
		switch(UIidx){//pa.score.staffs.size()
			case uiRawDataSourceIDX : {return getIDXofStringInArray(uiRawDataSourceList, dat);}
		}
		return -1;
	}//getIdxFromListStringIndiv
	
	public void setUI_JPFtrListMaxVals(int jpGrpLen, int jpLen) {
		//refresh max size of guiobj - heavy handed, these values won't change often, and this is called -every draw frame-.
		guiObjs[uiFtrJPToDispIDX].setNewMax(jpLen-1);
		guiObjs[uiFtrJPGToDispIDX].setNewMax(jpGrpLen-1);	
	}//setUI_JPListMaxVals
	
	public void setUI_JPAllSeenListMaxVals(int jpGrpLen, int jpLen) {
		//refresh max size of guiobj - heavy handed, these values won't change often, and this is called -every draw frame-.
		guiObjs[uiAllJpSeenToDispIDX].setNewMax(jpLen-1);
		//guiObjs[uiAllJpgSeenToDispIDX].setNewMax(jpGrpLen-1);	
	}//setUI_JPListMaxVals
	
	private boolean settingJPGFromJp = false, settingJPFromJPG = false;
	private boolean settingProdJPGFromJp = false, settingProdJPFromJPG = false;
	@Override 
	//handle instance-specific UI components
	protected void setUIWinValsIndiv(int UIidx) {
		//if(!(settingJPGFromJp || settingProdJPGFromJp || settingJPFromJPG || settingProdJPFromJPG)) {msgObj.dispInfoMessage("SOM WIN","setUIWinVals","Idx to set  : " + UIidx);}
		switch(UIidx){
			case uiRawDataSourceIDX  : {//source of raw data
				rawDataSource = (int)(this.guiObjs[uiRawDataSourceIDX].getVal());
				//change button display
				setCustMenuBtnNames();
				msgObj.dispMessage("mySOMMapUIWin","setUIWinVals","uiRawDataSourceIDX : rawDataSource set to : " + rawDataSource, MsgCodes.info1);
				break;}			
			case uiFtrJPGToDispIDX : {//highlight display of different region of SOM map corresponding to group of JPs (jpg)
				//msgObj.dispInfoMessage("SOM WIN","setUIWinVals::uiJPGToDispIDX", "Click : settingJPGFromJp : " + settingJPGFromJp);
				if(!settingJPGFromJp) {
					int curJPIdxVal = (int)guiObjs[uiFtrJPToDispIDX].getVal();
					int jpIdxToSet = ((Straff_SOMMapManager) mapMgr).getUI_FirstJPIdxFromFtrJPG((int)guiObjs[uiFtrJPGToDispIDX].getVal(), curJPIdxVal);
					if(curJPIdxVal != jpIdxToSet) {
						//msgObj.dispMessage("SOM WIN","setUIWinVals:uiJPGToDispIDX", "Attempt to modify uiJPToDispIDX : curJPIdxVal : "  +curJPIdxVal + " | jpToSet : " + jpIdxToSet, MsgCodes.info1);
						settingJPFromJPG = true;
						guiObjs[uiFtrJPToDispIDX].setVal(jpIdxToSet);	
						setUIWinValsIndiv(uiFtrJPToDispIDX);
						uiVals[uiFtrJPToDispIDX] =guiObjs[uiFtrJPToDispIDX].getVal();
						settingJPFromJPG = false;
					}
				}
				//msgObj.dispInfoMessage("SOM WIN","setUIWinVals::uiFtrJPGToDispIDX", "End : settingJPGFromJp : "+settingJPGFromJp+" | settingProdJPGFromJp : "+settingProdJPGFromJp+" | settingJPFromJPG : "+settingJPFromJPG+" | settingProdJPFromJPG : "+settingProdJPFromJPG);
				break;}
			case uiFtrJPToDispIDX : {//highlight display of different region of SOM map corresponding to selected JP				
				curMapImgIDX = (int)guiObjs[uiFtrJPToDispIDX].getVal();
				curProdToShowIDX = (int)guiObjs[uiFtrJPToDispIDX].getVal();
				//msgObj.dispInfoMessage("SOM WIN","setUIWinVals::uiJPToDispIDX", "Click : settingJPFromJPG : " + settingJPFromJPG);
				if(!settingJPFromJPG) {
					int curJPGIdxVal = (int)guiObjs[uiFtrJPGToDispIDX].getVal();
					int jpgIdxToSet = ((Straff_SOMMapManager) mapMgr).getUI_JPGrpFromFtrJP(curMapImgIDX, curJPGIdxVal);
					//fix this
					if(curJPGIdxVal != jpgIdxToSet) {
						//msgObj.dispMessage("SOM WIN","setUIWinVals::uiJPToDispIDX", "Attempt to modify uiJPGToDispIDX : cur JPG IDX val : "+ curJPGIdxVal + " | jpg IDX To Set : " + jpgIdxToSet, MsgCodes.info1);					
						settingJPGFromJp = true;
						guiObjs[uiFtrJPGToDispIDX].setVal(jpgIdxToSet);
						setUIWinValsIndiv(uiFtrJPGToDispIDX);
						uiVals[uiFtrJPGToDispIDX] =guiObjs[uiFtrJPGToDispIDX].getVal();
						settingJPGFromJp = false;
					}
				}
				//msgObj.dispInfoMessage("SOM WIN","setUIWinVals::uiFtrJPToDispIDX", "End : settingJPGFromJp : "+settingJPGFromJp+" | settingProdJPGFromJp : "+settingProdJPGFromJp+" | settingJPFromJPG : "+settingJPFromJPG+" | settingProdJPFromJPG : "+settingProdJPFromJPG);
				//msgObj.dispMessage("mySOMMapUIWin","setUIWinVals","uiJPToDispIDX : Setting UI JP Map to display to be idx :" + curMapImgIDX + " Corresponding to JP : " + mapMgr.getJpByIdxStr(curMapImgIDX) );					
				break;}
			
//			case uiAllJpgSeenToDispIDX		: {
//				if(!settingProdJPGFromJp) {
//					int curJPIdxVal = (int)guiObjs[uiAllJpSeenToDispIDX].getVal();
//					int jpIdxToSet = ((StraffSOMMapManager) mapMgr).getUI_FirstJPIdxFromAllJPG((int)guiObjs[uiAllJpgSeenToDispIDX].getVal(), curJPIdxVal);
//					//msgObj.dispInfoMessage("SOM WIN","setUIWinVals:uiJPGToDispIDX", "Attempt to modify uiJPToDispIDX : curJPVal : "  +curJPVal + " | jpToSet : " + jpToSet, MsgCodes.info1);
//					if(curJPIdxVal != jpIdxToSet) {
//						settingProdJPFromJPG = true;
//						guiObjs[uiAllJpSeenToDispIDX].setVal(jpIdxToSet);	
//						setUIWinValsIndiv(uiAllJpSeenToDispIDX);
//						uiVals[uiAllJpSeenToDispIDX] =guiObjs[uiAllJpSeenToDispIDX].getVal();
//						settingProdJPFromJPG = false;
//					}
//				}
//				break;}			
			case uiAllJpSeenToDispIDX		: {
				curAllJPToShowIDX = (int)guiObjs[uiAllJpSeenToDispIDX].getVal();
//				if(!settingProdJPFromJPG) {
//					int curJPGIdxVal = (int)guiObjs[uiAllJpgSeenToDispIDX].getVal();
//					int jpgIdxToSet = ((StraffSOMMapManager) mapMgr).getUI_JPGrpFromAllJP(curAllJPToShowIDX, curJPGIdxVal);
//					//msgObj.dispInfoMessage("SOM WIN","setUIWinVals::uiJPToDispIDX", "Attempt to modify uiJPGToDispIDX : cur JPG val : "+ curJPGVal + " | jpgToSet : " + jpgToSet, MsgCodes.info1);	
//					if(curJPGIdxVal != jpgIdxToSet) {
//						settingProdJPGFromJp = true;
//						guiObjs[uiAllJpgSeenToDispIDX].setVal(jpgIdxToSet);
//						setUIWinValsIndiv(uiAllJpgSeenToDispIDX);
//						uiVals[uiAllJpgSeenToDispIDX] =guiObjs[uiAllJpgSeenToDispIDX].getVal();
//						settingProdJPGFromJp = false;
//					}
//				}
				break;}	
			case uiProdZoneDistThreshIDX : {//max distance for a node to be considered a part of a product's "region" of influence		
				prodZoneDistThresh = this.guiObjs[uiProdZoneDistThreshIDX].getVal();			
				break;}
		}		
	}
	
	//modify menu buttons to display whether using CSV or SQL to access raw data
	@Override
	protected void setCustMenuBtnNames() {
		String rplStr = menuLdRawFuncBtnNames[(rawDataSource % menuLdRawFuncBtnNames.length)], baseStr;
		for(int i=0;i<menuBtnNames[mySideBarMenu.btnAuxFunc1Idx].length-2;++i) {
			baseStr = (String) menuBtnNames[mySideBarMenu.btnAuxFunc1Idx][i].subSequence(0, menuBtnNames[mySideBarMenu.btnAuxFunc1Idx][i].length()-3);
			menuBtnNames[mySideBarMenu.btnAuxFunc1Idx][i] = baseStr + rplStr;
		}
		//menuBtnNames[mySideBarMenu.btnAuxFunc1Idx][loadRawBtnIDX]=menuLdRawFuncBtnNames[(rawDataSource % menuLdRawFuncBtnNames.length) ];
		pa.setAllMenuBtnNames(menuBtnNames);	
	}//setCustMenuBtnNames
	
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
	protected void drawOnScreenStuffPriv(float modAmtMillis) {}

	@Override
	//draw 2d constructs over 3d area on screen - draws behind left menu section
	//modAmtMillis is in milliseconds
	protected void drawRightSideInfoBarPriv(float modAmtMillis) {
		pa.pushMatrix();pa.pushStyle();
		//display current simulation variables - call sim world through sim exec
		mapMgr.drawResultBar(pa, yOff);
		pa.popStyle();pa.popMatrix();					
	}//drawOnScreenStuff
	
	@Override
	//set flags that should be set on each frame - these are set at beginning of frame draw
	protected void drawSetDispFlags() {
		setPrivFlags(custExCalcedIDX, ((Straff_SOMMapManager) mapMgr).isFtrCalcDone(StraffWeightCalc.custCalcObjIDX));
		setPrivFlags(tpExCalcedIDX, ((Straff_SOMMapManager) mapMgr).isFtrCalcDone(StraffWeightCalc.tpCalcObjIDX));	
		//checking flag to execute if true
		if(getPrivFlags(procTruProspectsIDX)){	((Straff_SOMMapManager) mapMgr).buildAndSaveTrueProspectReport();setPrivFlags(procTruProspectsIDX,false);	}			
	}

	//stuff to draw specific to this instance, before nodes are drawn
	protected void drawMapRectangleIndiv(int curImgNum) {
		if(getPrivFlags(mapDrawTruePspctIDX)){			((Straff_SOMMapManager) mapMgr).drawTruPrspctData(pa);}
		//not drawing any analysis currently
		boolean notDrawAnalysis = !(getPrivFlags(mapDrawCustAnalysisVisIDX) || getPrivFlags(mapDrawTPAnalysisVisIDX));
		if (curImgNum > -1) {
			if (notDrawAnalysis && (mseOvrData != null)){	drawMseLocFtrs();}//draw mouse-over info if not showing calc analysis				
			if(getPrivFlags(mapDrawPrdctNodesIDX)){		((Straff_SOMMapManager) mapMgr).drawProductNodes(pa, curMapImgIDX, true);}
			if(getPrivFlags(mapDrawWtMapNodesIDX)){		mapMgr.drawNodesWithWt(pa, mapNodeWtDispThresh, curMapImgIDX);} 
		} else {//draw all products				
			if (notDrawAnalysis && (mseOvrData != null)){	drawMseLocWts();}
			if(getPrivFlags(mapDrawPrdctNodesIDX)){		((Straff_SOMMapManager) mapMgr).drawAllProductNodes(pa);}
		}
		
		if (getPrivFlags( mapDrawCurProdZoneIDX)){		((Straff_SOMMapManager) mapMgr).drawProductRegion(pa,curProdToShowIDX,prodZoneDistThresh);}
	}//drawMapRectangleIndiv
	
	
	@Override
	protected void drawMapIndiv() {
		
		if (getPrivFlags(mapDrawCustAnalysisVisIDX)){	_drawAnalysis(custExCalcedIDX, mapDrawCustAnalysisVisIDX);	} 
		else if (getPrivFlags(mapDrawTPAnalysisVisIDX)){_drawAnalysis(tpExCalcedIDX, mapDrawTPAnalysisVisIDX);}
	}
	
	
	private void _drawAnalysis(int exCalcedIDX, int mapDrawAnalysisIDX) {
		if (getPrivFlags(exCalcedIDX)){	
			//determine what kind of jps are being displayed
			//int curJPIdx = ( ? curMapImgIDX : curAllJPToShowIDX);
			pa.pushMatrix();pa.pushStyle();	
			pa.translate(calcAnalysisLocs[0],SOM_mapLoc[1]*calcScale + 10,0.0f);			
			if(curCalcAnalysisJPTypeIDX == Straff_SOMMapManager.jps_AllIDX) {		
				int curJPIdx = curAllJPToShowIDX;
				((Straff_SOMMapManager) mapMgr).drawAnalysisOneJp_All(pa,analysisHt, analysisPerJPWidth,curJPIdx, curCalcAnalysisTypeIDX);	
				pa.popStyle();pa.popMatrix();			
				pa.pushMatrix();pa.pushStyle();
				pa.translate(rectDim[0]+5,calcAnalysisLocs[1],0.0f);					
				((Straff_SOMMapManager) mapMgr).drawAnalysisAllJps(pa, analysisHt, analysisAllJPBarWidth, curJPIdx, curCalcAnalysisTypeIDX);
				
			} else if(curCalcAnalysisJPTypeIDX == Straff_SOMMapManager.jps_FtrIDX)  {		
				int curJPIdx = curMapImgIDX;
				((Straff_SOMMapManager) mapMgr).drawAnalysisOneJp_Ftr(pa,analysisHt, analysisPerJPWidth,curJPIdx, curCalcAnalysisTypeIDX);	
				pa.popStyle();pa.popMatrix();			
				pa.pushMatrix();pa.pushStyle();
				pa.translate(rectDim[0]+5,calcAnalysisLocs[1],0.0f);					
				((Straff_SOMMapManager) mapMgr).drawAnalysisFtrJps(pa, analysisHt, analysisAllJPBarWidth, curJPIdx, curCalcAnalysisTypeIDX);				
			}			
			
			pa.popStyle();pa.popMatrix();
			pa.scale(calcScale);				//scale here so that if we are drawing calc analysis, ftr map image will be shrunk
		} else {
			setPrivFlags(mapDrawAnalysisIDX, false);
		}
	}//_drawAnalysis
		
	//if launching threads for custom functions or debug, need to remove resetButtonState call in function below and call resetButtonState (with slow proc==true) when thread ends
	@Override
	protected void launchMenuBtnHndlr() {
		msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Begin requested action", MsgCodes.info4);
		int btn = curCustBtn[curCustBtnType];
		switch(curCustBtnType) {
		case mySideBarMenu.btnAuxFunc1Idx : {
			msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Click Functions 1 in "+name+" : btn : " + btn, MsgCodes.info4);
			switch(btn){
				case 0 : {	
					//load all data from raw local csvs or sql from db
					((Straff_SOMMapManager) mapMgr).loadAllRawData((rawDataSource==0));//, getPrivFlags(useOnlyEvntsToTrainIDX));
					resetButtonState();
					break;}
				case 1 : {	
					resetButtonState();
					break;}
				case 2 : {	
					//recalculate currently loaded/processed data.  will reload/rebuild calc object from file, so if data is changed in format file, this will handle that.
					((Straff_SOMMapManager) mapMgr).calcFtrsDiffsMinsAndSave();
					resetButtonState();
					break;}
				default : {
					msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Unknown Functions 1 btn : "+btn, MsgCodes.warning2);
					break;}
			}	
			break;}//row 1 of menu side bar buttons
		case mySideBarMenu.btnAuxFunc2Idx : {
			msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Click Functions 2 in "+name+" : btn : " + btn, MsgCodes.info4);//{"Ld&Bld SOM Data", "Load SOM Config", "Ld & Make Map", "Ld Prebuilt Map"},	//row 2
			//{"Ld Train data","Ld Prspcts", "Ld SOM Cfg", "Func 13", "Ld Prblt Map"},	//row 2
			switch(btn){
				case 0 : {	
					mapMgr.loadPreprocAndBuildTestTrainPartitions(getTrainTestDatPartition());
					resetButtonState();
					break;}
				case 1 : {	
					((Straff_SOMMapManager) mapMgr).loadAllTrueProspectData();
					resetButtonState();
					break;}
				case 2 : {	
					//this will load all true prospects from preprocessed prospect files.
					mapMgr.loadSOMConfig();//pass fraction of data to use for training
					resetButtonState();
					break;}
				case 3 : {//load all training data, default map config, and build map
					mapMgr.loadTrainDataMapConfigAndBuildMap();
					resetButtonState();
					break;}
				case 4 : {	
					mapMgr.loadPretrainedExistingMap();//runs in thread, button state reset there
					break;}
				default : {
					msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Unknown Functions 2 btn : "+btn, MsgCodes.warning2);
					break;}	
			}
			break;}//row 2 of menu side bar buttons
		case mySideBarMenu.btnDBGSelCmpIdx : {
			msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Click Debug in "+name+" : btn : " + btn, MsgCodes.info4);
			switch(btn){
				case 0 : {	
					((Straff_SOMMapManager) mapMgr).dbgShowAllRawData();
					resetButtonState();
					break;}
				case 1 : {	
					((Straff_SOMMapManager) mapMgr).dbgShowUniqueJPsSeen();
					((Straff_SOMMapManager) mapMgr).dbgShowCalcEqs();
					resetButtonState();
					break;}
				case 2 : {	
					((Straff_SOMMapManager) mapMgr).dbgShowJpJpgrpData();
					resetButtonState();
					break;}
				case 3 : {//show current mapdat status
					((Straff_SOMMapManager) mapMgr).dbgShowSOM_MapDat();
					resetButtonState();
					break;}
				case 4 : {						
					resetButtonState();
					break;}
				default : {
					msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Unknown Debug btn : "+btn, MsgCodes.warning2);
					break;}
			}				
			break;}//row 3 of menu side bar buttons (debug)			
		}		
		msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","End requested action (multithreaded actions may still be working).", MsgCodes.info4);
	}//launchMenuBtnHndlr
	
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
	public String toString(){
		String res = super.toString();
		return res;
	}

}//mySOMMapUIWin

/**
 * class to manage buttons used by sidebar window - overrides base setup
 * @author john
 *
 */
class mySideBarMenu extends BaseBarMenu {
	
	public static final int 
		//btnShowWinIdx = 0, 				//which window to show
		btnAuxFunc1Idx = 0,			//aux functionality 1
		btnAuxFunc2Idx = 1,			//aux functionality 2
		btnDBGSelCmpIdx = 2;			//debug
		//btnFileCmdIdx = 3;				//load/save files


	public mySideBarMenu(my_procApplet _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed,
			String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt, _canDrawTraj);
	}

	@Override
	protected void initSideBarMenuBtns_Priv() {
		guiBtnRowNames = new String[]{"Raw Data Conversion/Processing","Load Post Proc Data And Map Config/Exec","DEBUG"};//,"File"};

		//names for each row of buttons - idx 1 is name of row
		guiBtnNames = new String[][]{
			//new String[]{pa.winTitles[1], pa.winTitles[2], pa.winTitles[3], pa.winTitles[4]},							//display specific windows - multi-select/ always 
			new String[]{"Func 1","Func 2","Func 3"},						//per-window user functions - momentary
			new String[]{"Func 1","Func 2","Func 3","Func 4","Func 5"},						//per-window user functions - momentary
			new String[]{"Dbg 1","Dbg 2","Dbg 3","Dbg 4","Dbg 5"},						//DEBUG - momentary
//			new String[]{"Load Txt File","Save Txt File"}							//load an existing score, save an existing score - momentary		
		};
		//default names, to return to if not specified by user
		defaultUIBtnNames = new String[][]{
			//new String[]{pa.winTitles[1], pa.winTitles[2], pa.winTitles[3], pa.winTitles[4]},							//display specific windows - multi-select/ always 
			new String[]{"Func 1","Func 2","Func 3"},					//per-window user functions - momentary
			new String[]{"Func 1","Func 2","Func 3","Func 4","Func 5"},			//per-window user functions - momentary
			new String[]{"Dbg 1","Dbg 2","Dbg 3","Dbg 4","Dbg 5"},						//DEBUG - momentary
//			new String[]{"Load Txt File","Save Txt File"}							//load an existing score, save an existing score - momentary		
		};
		//whether buttons are momentary or not (on only while being clicked)
		guiBtnInst = new boolean[][]{
			//new boolean[]{false,false,false,false},         						//display specific windows - multi-select/ always on if sel
			new boolean[]{false,false,false,false,false},                   //functionality - momentary
			new boolean[]{false,false,false,false,false},                   //functionality - momentary
			new boolean[]{false,false,false,false,false},                   		//debug - momentary
//			new boolean[]{true,true},			              			//load an existing score, save an existing score - momentary	
		};		
		//whether buttons are waiting for processing to complete (for non-momentary buttons)
		guiBtnWaitForProc = new boolean[][]{
			//new boolean[]{false,false,false,false},         						//display specific windows - multi-select/ always on if sel
			new boolean[]{false,false,false,false,false},                   //functionality - momentary
			new boolean[]{false,false,false,false,false},                   //functionality - momentary
			new boolean[]{false,false,false,false,false},                   		//debug - momentary
//			new boolean[]{false,false},			              			//load an existing score, save an existing score - momentary	
		};			
		
		//whether buttons are disabled(-1), enabled but not clicked/on (0), or enabled and on/clicked(1)
		guiBtnSt = new int[][]{
			//new int[]{0,0,1,0},                    					//display specific windows - multi-select/ always on if sel
			new int[]{0,0,0,0,0},                   					//debug - momentary
			new int[]{0,0,0,0,0},                   					//debug - momentary
			new int[]{0,0,0,0,0},                   					//debug - momentary
//			new int[]{0,0}			              					//load an existing score, save an existing score - momentary	
		};	}

	@Override
	public void handleButtonClick(int row, int col){
		int val = guiBtnSt[row][col];//initial state, before being changed
		guiBtnSt[row][col] = (guiBtnSt[row][col] + 1)%2;//change state
		//if not momentary buttons, set wait for proc to true
		setWaitForProc(row,col);
		switch(row){
			//case btnShowWinIdx 		: {pa.handleShowWin(col, val);break;}
			case btnAuxFunc1Idx 		: //{pa.handleMenuBtnSelCmp(btnAuxFunc1Idx,col, val);break;}
			case btnAuxFunc2Idx 		: //{pa.handleMenuBtnSelCmp(btnAuxFunc2Idx,col, val);break;}
			case btnDBGSelCmpIdx  		: {pa.handleMenuBtnSelCmp(row, col, val);break;}//{pa.handleMenuBtnSelCmp(btnDBGSelCmpIdx,col, val);break;}
//			case btnFileCmdIdx 			: {pa.handleFileCmd(btnFileCmdIdx, col, val);break;}
		}				
	}	

	@Override
	protected void launchMenuBtnHndlr() {
		switch(curCustBtnType) {
		case mySideBarMenu.btnAuxFunc1Idx : {break;}//row 1 of menu side bar buttons
		case mySideBarMenu.btnAuxFunc2Idx : {break;}//row 2 of menu side bar buttons
		case mySideBarMenu.btnDBGSelCmpIdx : {break;}//row 3 of menu side bar buttons (debug)			
		}		
	}

}//mySideBarMenu
