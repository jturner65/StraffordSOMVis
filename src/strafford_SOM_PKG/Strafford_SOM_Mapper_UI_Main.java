package strafford_SOM_PKG;

import java.io.File;
import java.util.HashMap;

import base_UI_Objects.GUI_AppManager;
import base_Utils_Objects.io.messaging.MsgCodes;
import strafford_SOM_PKG.straff_UI.Straff_SOMMapUIWin;
/**
 * Testbed to visually inspect and verify results from Strafford prospect mapping to a SOM
 * 
 * John Turner
 * 
 */
public class Strafford_SOM_Mapper_UI_Main extends GUI_AppManager {
	//project-specific variables

	private final String prjNmShrt = "SOM_Strafford";
	private final String prjNmLong = "Testbed for development of Strafford Prospects SOM";	
	private final String projDesc = "UI-enabled testing platform to preprocess raw customer, prospect and product data, train a self organizing map on the customers and use the map to draw suggest likely products for customers and prospects.";

	private static final int numVisWins = 2;
	
	//idx's in dispWinFrames for each window - 0 is always left side menu window
	private static final int	dispSOMMapIDX = 1;	
																		//set array of vector values (sceneFcsVals) based on application
	//private boolean cyclModCmp;										//comparison every draw of cycleModDraw			
	private final int[] bground = new int[]{244,244,244,255};		//bground color	
	
	/**
	 * Labels for buttons that describe what mouse-over on the SOM displays
	 */
	public static final String[] MseOvrLblsAra = new String[]{"Loc","Dist","Pop","Ftr","JP","JPGrp","None"};
///////////////
//CODE STARTS
///////////////	
	//////////////////////////////////////////////// code
	
	//needs main to run project - do not modify this code in any way
	//appletArgs = array of single string holding  <pkgname>.<classname> of this class
	public static void main(String[] passedArgs) {		
		Strafford_SOM_Mapper_UI_Main me = new Strafford_SOM_Mapper_UI_Main();
		Strafford_SOM_Mapper_UI_Main.invokeProcessingMain(me, passedArgs);
	}//main	

	public Strafford_SOM_Mapper_UI_Main() {super();}

	@Override
	protected boolean showMachineData() {return true;}
	/**
	 * Set various relevant runtime arguments in argsMap
	 * @param _passedArgs command-line arguments
	 */
	@Override
	protected HashMap<String,Object> setRuntimeArgsVals(HashMap<String, Object> _passedArgsMap) {
		HashMap<String, Object> argsMap = new HashMap<String, Object>();
		//provide default values used by SOM program
		argsMap.put("configDir", "StraffordProject" + File.separator+"config" + File.separator);
		argsMap.put("dataDir", "StraffordProject" + File.separator);
		argsMap.put("logLevel",2);//0 is console alone,1 is log file alone, 2 is both
		return argsMap;
	}

	/**
	 * whether or not we want to restrict window size on widescreen monitors
	 * 
	 * @return 0 - use monitor size regardless
	 * 			1 - use smaller dim to be determine window 
	 * 			2+ - TBD
	 */
	@Override
	protected int setAppWindowDimRestrictions() {	return 1;}	
	/**
	 * Called in pre-draw initial setup, before first init
	 * potentially override setup variables on per-project basis.
	 * Do not use for setting background color or Skybox anymore.
	 *  	(Current settings in my_procApplet) 	
	 *  	strokeCap(PROJECT);
	 *  	textSize(txtSz);
	 *  	textureMode(NORMAL);			
	 *  	rectMode(CORNER);	
	 *  	sphereDetail(4);	 * 
	 */
	@Override
	protected void setupAppDims_Indiv() {}
	@Override
	protected boolean getUseSkyboxBKGnd(int winIdx) {	return false;}
	@Override
	protected String getSkyboxFilename(int winIdx) {	return "";}
	@Override
	protected int[] getBackgroundColor(int winIdx) {return bground;}
	@Override
	protected int getNumDispWindows() {	return numVisWins;	}	

	@Override
	protected void setSmoothing() {		ri.setSmoothing(0);		}

	@Override
	public String getPrjNmLong() {return prjNmLong;}
	@Override
	public String getPrjNmShrt() {return prjNmShrt;}
	@Override
	public String getPrjDescr() {return projDesc;}

	/**
	 * Set minimum level of message object console messages to display for this application. If null then all messages displayed
	 * @return
	 */
	@Override
	protected final MsgCodes getMinConsoleMsgCodes() {return null;}
	/**
	 * Set minimum level of message object log messages to save to log for this application. If null then all messages saved to log.
	 * @return
	 */
	@Override
	protected final MsgCodes getMinLogMsgCodes() {return null;}

	/**
	 * determine which main flags to show at upper left of menu 
	 */
	@Override
	protected void initBaseFlags_Indiv() {
		setBaseFlagToShow_debugMode(false);
		setBaseFlagToShow_saveAnim(true); 
		setBaseFlagToShow_runSim(false);
		setBaseFlagToShow_singleStep(false);
		setBaseFlagToShow_showRtSideMenu(true);	
		setBaseFlagToShow_showStatusBar(true);
		setBaseFlagToShow_showDrawableCanvas(false);
	}
	
	@Override
	//build windows here
	protected void initAllDispWindows() {
		showInfo = true;	
		//titles and descs, need to be set before sidebar menu is defined
		String[] _winTitles = new String[]{"","SOM Map UI"},
				_winDescr = new String[] {"", "Visualize Prospect SOM Node Mapping"};

		//instanced window dims when open and closed - only showing 1 open at a time - and init cam vals
		float[][] _floatDims  = getDefaultWinAndCameraDims();	
		//Builds sidebar menu button config - application-wide menu button bar titles and button names
		String[] menuBtnTitles = new String[]{"Raw Data Conversion/Processing","Load Post Proc Data","Console Exec Testing","Load Prebuilt Maps"};

		String[][] menuBtnNames = new String[][] {	//each must have literals for every button defined in side bar menu, or ignored
			{"Load All Raw ---", "---","Recalc Features"},	//row 1
			{"Train Data","True Prspcts", "Prods", "SOM Cfg"},	//row 2
			{"Train->Bld Map","Map All Data", "---", "---"},	//row 3
			{"Map 1","Map 2","Map 3","Map 4"},
			{"Raw","Proced","JpJpg","MapDat","---"}	
		};
		String[] dbgBtnNames = new String[] {"Debug 0","Debug 1","Debug 2","Debug 3","Debug 4"};	
		buildSideBarMenu(_winTitles,menuBtnTitles, menuBtnNames, dbgBtnNames, false, true);//new Straff_SOMMapUISideBarMenu(this, winTitles[wIdx], fIdx, winFillClrs[wIdx], winStrkClrs[wIdx], winRectDimOpen[wIdx], winRectDimClose[wIdx], winDescr[wIdx]);	

		//define windows
		/**
		 *  _winIdx The index in the various window-descriptor arrays for the dispWindow being set
		 *  _title string title of this window
		 *  _descr string description of this window
		 *  _dispFlags Essential flags describing the nature of the dispWindow for idxs : 
		 * 		0 : dispWinIs3d, 
		 * 		1 : canDrawInWin; 
		 * 		2 : canShow3dbox (only supported for 3D); 
		 * 		3 : canMoveView
		 *  _floatVals an array holding float arrays for 
		 * 				rectDimOpen(idx 0),
		 * 				rectDimClosed(idx 1),
		 * 				initCameraVals(idx 2)
		 *  _intClrVals and array holding int arrays for
		 * 				winFillClr (idx 0),
		 * 				winStrkClr (idx 1),
		 * 				winTrajFillClr(idx 2),
		 * 				winTrajStrkClr(idx 3),
		 * 				rtSideFillClr(idx 4),
		 * 				rtSideStrkClr(idx 5)
		 *  _sceneCenterVal center of scene, for drawing objects (optional)
		 *  _initSceneFocusVal initial focus target for camera (optional)
		 */
		int wIdx = dispSOMMapIDX;		
		setInitDispWinVals(wIdx, _winTitles[wIdx], _winDescr[wIdx],	getDfltBoolAra(false), _floatDims,
				new int[][] {new int[]{50,40,20,255}, new int[]{255,255,255,255},
					new int[] {120,120,120,255},new int[]{50,40,20,255},
					new int[]{0,0,0,200},new int[]{255,255,255,255}});
		
		setDispWindow(wIdx, new Straff_SOMMapUIWin(ri, this, winInitVals[wIdx]));		
		//specify windows that cannot be shown simultaneously here
		initXORWins(new int[]{dispSOMMapIDX},new int[]{dispSOMMapIDX});
	}//	initVisOnce_Priv
	
	@Override
	//called from base class, once at start of program after vis init is called - set initial windows to show - always show UI Menu
	protected void initOnce_Indiv(){
		//which objects to initially show
		setWinVisFlag(dispSOMMapIDX, true);
		setShowStatusBar(true);
	}//	initOnce
	
	@Override
	//called multiple times, whenever re-initing
	protected void initProgram_Indiv(){	}//initProgram	

	
	/**
	 * Individual extending Application Manager post-drawMe functions
	 * @param modAmtMillis
	 * @param is3DDraw
	 */
	@Override
	protected void drawMePost_Indiv(float modAmtMillis, boolean is3DDraw) {}
	
	/**
	 * present an application-specific array of mouse over btn names 
	 * for the selection of the desired mouse over text display - if is length 0 or null, will not be displayed
	 */
	@Override
	public String[] getMouseOverSelBtnLabels() {
		return MseOvrLblsAra;
	}
	
	//////////////////////////////////////////////////////
	/// user interaction
	//////////////////////////////////////////////////////	
	//key is key pressed
	//keycode is actual physical key pressed == key if shift/alt/cntl not pressed.,so shift-1 gives key 33 ('!') but keycode 49 ('1')
	
	/**
	 * handle non-numeric keys being pressed
	 * @param keyVal character of key having been pressed
	 * @param keyCode actual code of key having been pressed
	 */
	@Override
	protected void handleKeyPress(char keyVal, int keyCode) {
		switch (keyVal){
			case ' ' : {toggleSimIsRunning(); break;}							//run sim
			case 'f' : {getCurFocusDispWindow().setInitCamView();break;}					//reset camera
			case 'a' :
			case 'A' : {toggleSaveAnim();break;}						//start/stop saving every frame for making into animation
			case 's' :
			case 'S' : {break;}//{saveSS(prjNmShrt);break;}//save picture of current image			
			default : {	}
		}//switch	
		
		
	}//handleNonNumberKeyPress

	//keys/criteria are present that means UI objects are modified by set values based on clicks (as opposed to dragging for variable values)
	//to facilitate UI interaction non-mouse computers, set these to be single keys
	@Override
	public boolean isClickModUIVal() {
		//TODO change this to manage other key settings for situations where multiple simultaneous key presses are not optimal or conventient
		return altIsPressed() || shiftIsPressed();		
	}
	
	@Override
	//get the ui rect values of the "master" ui region (another window) -> this is so ui objects of one window can be made, clicked, and shown displaced from those of the parent windwo
	public float[] getUIRectVals_Indiv(int idx, float[] menuClickDim){
		//this.pr("In getUIRectVals for idx : " + idx);
		switch(idx){
		case dispSOMMapIDX 	: {	return menuClickDim;}
		default :  return menuClickDim;
		}
	}	
	
	//////////////////////////////////////////
	/// graphics and base functionality utilities and variables
	//////////////////////////////////////////
	
	/**
	 * return the number of visible window flags for this application
	 * @return
	 */
	@Override
	public int getNumVisFlags() {return numVisWins;}
	@Override
	//address all flag-setting here, so that if any special cases need to be addressed they can be
	protected void setVisFlag_Indiv(int idx, boolean val ){
		switch (idx){
			case dispSOMMapIDX		: {setWinFlagsXOR(dispSOMMapIDX, val); break;}
			default : {break;}
		}
	}//setFlags  


	/**
	 * any instancing-class-specific colors - colorVal set to be higher than IRenderInterface.gui_OffWhite
	 * @param colorVal
	 * @param alpha
	 * @return
	 */
	@Override
	public int[] getClr_Custom(int colorVal, int alpha) {	return new int[] {255,255,255,alpha};}

}//class SOM_StraffordMain
