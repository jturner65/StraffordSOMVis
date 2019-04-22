package SOM_Strafford_PKG;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;			//used for threading

import UI.*;
import Utils.*;
import processing.core.*;
/**
 * Testbed to visually inspect and verify results from Strafford prospect mapping to a SOM
 * 
 * John Turner
 * 
 */
public class SOM_StraffordMain extends my_procApplet {
	//project-specific variables
	public String prjNmLong = "Testbed for development of Strafford Prospects SOM", prjNmShrt = "SOM_Strafford";
	
	//data in files created by SOM_MAP separated by spaces
	public String SOM_FileToken = " ", csvFileToken = "\\s*,\\s*";
	//platform independent path separator
	public String dirSep = File.separator;
	//don't use sphere background for this program
	private boolean useSphereBKGnd = false;	
			
	public String exeDir = Paths.get("").toAbsolutePath().toString();
	//base directory to write output from spheres/samples and from som analysis
	public String BaseWriteDir = exeDir + dirSep + "data" + dirSep;
	//location of SOM_MAP exe
	//NOTE this program uses a modified version of the SOM_MAP library, modified to also build a file holding the per-feature best nodes
	public String SOM_Dir = BaseWriteDir;
			
	//holds training data
	public boolean init = false; 
	
	//2D, 3D
	private myVector[] sceneFcsValsBase = new myVector[]{						//set these values to be different targets of focus
			new myVector(-grid2D_X/2,-grid2D_Y/1.75f,0),
			new myVector(0,0,0)
	};
	//2D, 3D
	private myPoint[] sceneCtrValsBase = new myPoint[]{				//set these values to be different display center translations -
		new myPoint(0,0,0),										// to be used to calculate mouse offset in world for pick
		new myPoint(-gridDimX/2.0,-gridDimY/2.0,-gridDimZ/2.0)
	};
	
	
	public myVector[] cameraInitLocs = new myVector[]{						//set these values to be different initial camera locations based on 2d or 3d
			new myVector(camInitRx,camInitRy,camInitialDist),
			new myVector(camInitRx,camInitRy,camInitialDist),
			new myVector(-0.47f,-0.61f,-gridDimZ*.25f)			
	};
	
	private int[] visFlags;
	public final int
		showUIMenu = 0,
		showSOMMapUI = 1;
	public final int numVisFlags = 2;
	
	//idx's in dispWinFrames for each window - 0 is always left side menu window
	public static final int	dispSOMMapIDX = 1;	
																		//set array of vector values (sceneFcsVals) based on application
	//private boolean cyclModCmp;										//comparison every draw of cycleModDraw			
	public final int[] bground = new int[]{244,244,244,255};		//bground color
	private PShape bgrndSphere;										//giant sphere encapsulating entire scene


///////////////
//CODE STARTS
///////////////	
	//////////////////////////////////////////////// code
	public static void main(String[] passedArgs) {		
		String[] appletArgs = new String[] { "SOM_Strafford_PKG.SOM_StraffordMain" };
	    if (passedArgs != null) {PApplet.main(PApplet.concat(appletArgs, passedArgs)); } else {PApplet.main(appletArgs);		    }
	}//main
	public void settings(){
		size((int)(displayWidth*.95f), (int)(displayHeight*.92f),P3D);
		noSmooth();
	}		

	public void setup() {
		colorMode(RGB, 255, 255, 255, 255);
		frameRate(frate);
		if(useSphereBKGnd) {			setBkgndSphere();		} 
		else {			setBkgrnd();		}
		initVisOnce();
		//call this in first draw loop?
		initOnce();
	}// setup
	
	private void setBkgndSphere() {
		sphereDetail(100);
		//TODO move to window to set up specific background for each different "scene" type
		PImage bgrndTex = loadImage("bkgrndTex.jpg");
		bgrndSphere = createShape(SPHERE, 10000);
		bgrndSphere.setTexture(bgrndTex);
		bgrndSphere.rotate(HALF_PI,-1,0,0);
		bgrndSphere.setStroke(false);	
		//TODO move to myDispWindow
		background(bground[0],bground[1],bground[2],bground[3]);		
		shape(bgrndSphere);	
	}
	
	public void setBkgrnd(){
		background(bground[0],bground[1],bground[2],bground[3]);		
	}//setBkgrnd
	//called once at start of program
	public void initOnce(){
		numThreadsAvail = Runtime.getRuntime().availableProcessors();
		pr("# threads : "+ numThreadsAvail);
		th_exec = Executors.newFixedThreadPool(numThreadsAvail);
		//th_exec = Executors.newCachedThreadPool();			 
		initDispWins();
		setVisFlag(showUIMenu, true);					//show input UI menu	
		setVisFlag(showSOMMapUI, true);
		initProgram();
		setFinalInitDone(true);
	}//	initOnce
	
	//called multiple times, whenever re-initing
	public void initProgram(){
		initVisProg();				//always first
		drawCount = 0;
	}//initProgram
	
	//get difference between frames and set both glbl times
	private float getModAmtMillis() {
		glblStartSimFrameTime = millis();
		float modAmtMillis = (glblStartSimFrameTime - glblLastSimFrameTime);
		glblLastSimFrameTime = millis();
		return modAmtMillis;
	}
	
	//main draw loop
	public void draw(){	
		if(!isFinalInitDone()) {initOnce(); return;}	
		float modAmtMillis = getModAmtMillis();
		//simulation section
		if(isRunSim() ){
			//run simulation
			drawCount++;									//needed here to stop draw update so that pausing sim retains animation positions	
			for(int i =1; i<numDispWins; ++i){if((isShowingWindow(i)) && (dispWinFrames[i].getFlags(myDispWindow.isRunnable))){dispWinFrames[i].simulate(modAmtMillis);}}
			if(isSingleStep()){setSimIsRunning(false);}
			simCycles++;
		}		//play in current window

		//drawing section
		pushMatrix();pushStyle();
		drawSetup();																//initialize camera, lights and scene orientation and set up eye movement		
		if((curFocusWin == -1) || (dispWinIs3D[curFocusWin])){	//allow for single window to have focus, but display multiple windows	
			//if refreshing screen, this clears screen, sets background
			setBkgrnd();				
			draw3D_solve3D(modAmtMillis);
			c.buildCanvas();			
			if(canShow3DBox[curFocusWin]){drawBoxBnds();}
			if(dispWinFrames[curFocusWin].chkDrawMseRet()){			c.drawMseEdge();	}			
			popStyle();popMatrix(); 
		} else {	//either/or 2d window
			//2d windows paint window box so background is always cleared
			c.buildCanvas();
			c.drawMseEdge();
			popStyle();popMatrix(); 
			for(int i =1; i<numDispWins; ++i){if (isShowingWindow(i) && !(dispWinFrames[i].getFlags(myDispWindow.is3DWin))){dispWinFrames[i].draw2D(modAmtMillis);}}
		}
		drawUI(modAmtMillis);																	//draw UI overlay on top of rendered results			
		if (doSaveAnim()) {	savePic();}
		updateConsoleStrs();
		surface.setTitle(prjNmLong + " : " + (int)(frameRate) + " fps|cyc curFocusWin : " + curFocusWin);
	}//draw
	
	private void updateConsoleStrs(){
		++drawCount;
		if(drawCount % cnslStrDecay == 0){drawCount = 0;	consoleStrings.poll();}			
	}//updateConsoleStrs
	
	public void draw3D_solve3D(float modAmtMillis){
		//System.out.println("drawSolve");
		pushMatrix();pushStyle();
		for(int i =1; i<numDispWins; ++i){
			if((isShowingWindow(i)) && (dispWinFrames[i].getFlags(myDispWindow.is3DWin))){
				dispWinFrames[i].draw3D(modAmtMillis);
			}
		}
		popStyle();popMatrix();
		//fixed xyz rgb axes for visualisation purposes and to show movement and location in otherwise empty scene
		drawAxes(100,3, new myPoint(-c.viewDimW/2.0f+40,0.0f,0.0f), 200, false); 		
	}//draw3D_solve3D
	
	//if should show problem # i
	public boolean isShowingWindow(int i){return getVisFlag((i+this.showUIMenu));}//showUIMenu is first flag of window showing flags
	
	//handle pressing keys 0-9
	//keyVal is actual value of key (screen character as int)
	//keyPressed is actual key pressed (shift-1 gives keyVal 33 ('!') but keyPressed 49 ('1')) 
	//need to subtract 48 from keyVal or keyPressed to get actual number
	protected void handleNumberKeyPress(int keyVal, int keyPressed) {
		//use key if want character 
		if (key == '0') {
		} 
		
	}//handleNumberKeyPress
	
	//////////////////////////////////////////////////////
	/// user interaction
	//////////////////////////////////////////////////////	
	//key is key pressed
	//keycode is actual physical key pressed == key if shift/alt/cntl not pressed.,so shift-1 gives key 33 ('!') but keycode 49 ('1')
	public void keyPressed(){
		if(key==CODED) {
			if(!shiftIsPressed()){setShiftPressed(keyCode  == 16);} //16 == KeyEvent.VK_SHIFT
			if(!cntlIsPressed()){setCntlPressed(keyCode  == 17);}//17 == KeyEvent.VK_CONTROL			
			if(!altIsPressed()){setAltPressed(keyCode  == 18);}//18 == KeyEvent.VK_ALT
		} else {	
			//handle pressing keys 0-9 (with or without shift,alt, cntl)
			if ((keyCode>=48) && (keyCode <=57)) { handleNumberKeyPress(((int)key),keyCode);}
			else {					//handle all other (non-numeric) keys
				switch (key){
					case ' ' : {toggleSimIsRunning(); break;}							//run sim
					case 'f' : {dispWinFrames[curFocusWin].setInitCamView();break;}					//reset camera
					case 'a' :
					case 'A' : {toggleSaveAnim();break;}						//start/stop saving every frame for making into animation
					case 's' :
					case 'S' : {save(getScreenShotSaveName(prjNmShrt));break;}//save picture of current image			
					default : {	}
				}//switch	
			}
		}
	}//keyPressed()

	@Override
	//gives multiplier based on whether shift, alt or cntl (or any combo) is pressed
	public double clickValModMult(){return ((altIsPressed() ? .1 : 1.0) * (shiftIsPressed() ? 10.0 : 1.0));}	
	//keys/criteria are present that means UI objects are modified by set values based on clicks (as opposed to dragging for variable values)
	//to facilitate UI interaction non-mouse computers, set these to be single keys
	@Override
	public boolean isClickModUIVal() {
		//TODO change this to manage other key settings for situations where multiple simultaneous key presses are not optimal or conventient
		return altIsPressed() || shiftIsPressed();		
	}
	
	@Override
	//these tie using the UI buttons to modify the window in with using the boolean tags - PITA but currently necessary
	public void handleShowWin(int btn, int val, boolean callFlags){//display specific windows - multi-select/ always on if sel
		if(!callFlags){//called from setflags - only sets button state in UI to avoid infinite loop
			//setMenuBtnState(mySideBarMenu.btnShowWinIdx,btn, val);
		} else {//called from clicking on buttons in UI
		//val is btn state before transition 
		boolean bVal = (val == 1?  false : true);
		switch(btn){
			case 0 : {setVisFlag(showSOMMapUI, bVal);break;}
			}
		}
	}//handleShowWin
	

	public void loadFromFile(File file){
		if (file == null) {
		    outStr2Scr("Load was cancelled.");
		    return;
		} 
		String[] res = loadStrings(file.getAbsolutePath());
		//stop any simulations while file is loaded
		//load - iterate through for each window
		int[] stIdx = {0};//start index for a particular window - make an array so it can be passed by ref and changed by windows
		for(int i =0; i<numDispWins; ++i){
			while(!res[stIdx[0]].contains(dispWinFrames[i].name)){++stIdx[0];}			
			dispWinFrames[i].hndlFileLoad(res,stIdx);
		}//accumulate array of params to save
		//resume simulations		
	}//loadFromFile
	
	public void saveToFile(File file){
		if (file == null) {
		    outStr2Scr("Save was cancelled.");
		    return;
		} 
		ArrayList<String> res = new ArrayList<String>();
		//save - iterate through for each window
		for(int i =0; i<numDispWins; ++i){
			res.addAll(dispWinFrames[i].hndlFileSave());	
		}//accumulate array of params to save
		saveStrings(file.getAbsolutePath(), res.toArray(new String[0]));  
	}//saveToFile	
	
	public void initDispWins(){
		//instanced window dimensions when open and closed - only showing 1 open at a time
		winRectDimOpen[dispSOMMapIDX]  =  new float[]{menuWidth, 0, width-menuWidth, height};
		//hidden
		winRectDimClose[dispSOMMapIDX]  =  new float[]{menuWidth, 0, hideWinWidth, height};
		
		winTrajFillClrs = new int []{gui_Black,gui_LightGray,gui_LightGreen};		//set to color constants for each window
		winTrajStrkClrs = new int []{gui_Black,gui_DarkGray,gui_White};				//set to color constants for each window			
		
	//			//display window initialization	
		int wIdx = dispSOMMapIDX,fIdx=showSOMMapUI;
		dispWinFrames[wIdx] = new mySOMMapUIWin(this, winTitles[wIdx], fIdx,winFillClrs[wIdx], winStrkClrs[wIdx], winRectDimOpen[wIdx], winRectDimClose[wIdx], winDescr[wIdx],canDrawInWin[wIdx]);
		for(int i =0; i < numDispWins; ++i){
			int scIdx = dispWinIs3D[i] ? 1 : 0;
			dispWinFrames[i].finalInit(dispWinIs3D[i], canMoveView[i], sceneCtrValsBase[scIdx], sceneFcsValsBase[scIdx]);
			dispWinFrames[i].setTrajColors(winTrajFillClrs[i], winTrajStrkClrs[i]);
			dispWinFrames[i].setRtSideUIBoxClrs(new int[]{0,0,0,200},new int[]{255,255,255,255});
		}	
		//set initial state to be true - show info window
		//setFlags(showRtSideMenu, true);
	}//initDispWins
	
	@Override
	//get the ui rect values of the "master" ui region (another window) -> this is so ui objects of one window can be made, clicked, and shown displaced from those of the parent windwo
	public float[] getUIRectVals(int idx){
		//this.pr("In getUIRectVals for idx : " + idx);
		switch(idx){
		case dispMenuIDX 		: {return new float[0];}			//idx 0 is parent menu sidebar
		case dispSOMMapIDX 	: {	return dispWinFrames[dispMenuIDX].uiClkCoords;}
		default :  return dispWinFrames[dispMenuIDX].uiClkCoords;
		}
	}	
	
	//////////////////////////////////////////
	/// graphics and base functionality utilities and variables
	//////////////////////////////////////////

	@Override
	protected  void initVisOnce_Priv() {
		showInfo = true;
		drawnTrajEditWidth = 10;
		numDispWins = 2;	//must be set here!
		winRectDimOpen = new float[numDispWins][];
		winRectDimClose = new float[numDispWins][];
		winRectDimOpen[0] =  new float[]{0,0, menuWidth, height};
		winRectDimClose[0] =  new float[]{0,0, hideWinWidth, height};
		
		//need 1 per display window
		winTitles = new String[]{"","SOM Map UI"};
		winDescr = new String[] {"", "Visualize Prospect SOM Node Mapping"};

		//whether or not the display windows will accept a drawn trajectory
		canDrawInWin = new boolean[]{false,false};		
		canShow3DBox = new boolean[]{false,false};		
		canMoveView = new boolean[]{false,false};		
		dispWinIs3D = new boolean[]{false,false};
		
		winFillClrs = new int[][]{          
			new int[]{255,255,255,255},                                 	// dispMenuIDX = 0,
			new int[]{50,40,20,255}                                        	// dispSOMMapIDX = 2
		};
		winStrkClrs = new int[][]{
			new int[]{0,0,0,255},                                    		//dispMenuIDX = 0,
			new int[]{255,255,255,255}                               		//dispSOMMapIDX = 2
		};
		
		winTrajFillClrs = new int []{0,0};		//set to color constants for each window
		winTrajStrkClrs = new int []{0,0};		//set to color constants for each window
		

		//display window initialization
		dispWinFrames = new myDispWindow[numDispWins];		
		//menu bar init
		dispWinFrames[dispMenuIDX] = new mySideBarMenu(this, "UI Window", showUIMenu,  winFillClrs[dispMenuIDX], winStrkClrs[dispMenuIDX], winRectDimOpen[dispMenuIDX],winRectDimClose[dispMenuIDX], "User Controls",canDrawInWin[dispMenuIDX]);			

		winFlagsXOR = new int[]{showSOMMapUI};//showSequence,showSphereUI};
		//specify windows that cannot be shown simultaneously here
		winDispIdxXOR = new int[]{dispSOMMapIDX};//dispPianoRollIDX,dispSphereUIIDX};
		initVisFlags();

	}//	initVisOnce_Priv
	
		//init boolean state machine flags for program
	public void initVisFlags(){
		visFlags = new int[1 + numVisFlags/32];for(int i =0; i<numVisFlags;++i){forceVisFlag(i,false);}	

		((mySideBarMenu)dispWinFrames[dispMenuIDX]).initPFlagColors();			//init sidebar window flags
	}		
	
	//address all flag-setting here, so that if any special cases need to be addressed they can be
	public void setVisFlag(int idx, boolean val ){
		int flIDX = idx/32, mask = 1<<(idx%32);
		visFlags[flIDX] = (val ?  visFlags[flIDX] | mask : visFlags[flIDX] & ~mask);
		switch (idx){
			case showUIMenu 	    : { dispWinFrames[dispMenuIDX].setFlags(myDispWindow.showIDX,val);    break;}											//whether or not to show the main ui window (sidebar)			
			case showSOMMapUI		: {setWinFlagsXOR(dispSOMMapIDX, val); break;}
			//case showSOMMapUI 		: {dispWinFrames[dispSOMMapIDX].setFlags(myDispWindow.showIDX,val);handleShowWin(dispSOMMapIDX-1 ,(val ? 1 : 0),false); setWinsHeight(dispSOMMapIDX); break;}	//show InstEdit window
	
			//case useDrawnVels 		: {for(int i =1; i<dispWinFrames.length;++i){dispWinFrames[i].rebuildAllDrawnTrajs();}break;}
			default : {break;}
		}
	}//setFlags  
	//get vis flag
	public boolean getVisFlag(int idx){int bitLoc = 1<<(idx%32);return (visFlags[idx/32] & bitLoc) == bitLoc;}	
	public void forceVisFlag(int idx, boolean val) {
		int flIDX = idx/32, mask = 1<<(idx%32);
		visFlags[flIDX] = (val ?  visFlags[flIDX] | mask : visFlags[flIDX] & ~mask);
		//doesn't perform any other ops - to prevent 
	}
	
//	//set flags appropriately when only 1 can be true 
//	public void setFlagsXOR(int tIdx, int[] fIdx){for(int i =0;i<fIdx.length;++i){if(tIdx != fIdx[i]){flags[fIdx[i]] =false;}}}						


}//class SOM_StraffordMain
