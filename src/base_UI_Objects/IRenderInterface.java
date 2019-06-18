/**
 * 
 */
package base_UI_Objects;

/**
 * These are the functions that are expected to be found in a rendering class for proper rendering 
 * @author john
 *
 */
public interface IRenderInterface {
	
	
	public void pushMatrix();
	
	public void popMatrix();
	
	public void pushStyle();
	
	public void popStyle();
	
	//TODO put all functions commonly used from myDispWindow and its inheritors in here to support different rendering engines

}//IRenderInterface
