# SOMSpheres
(Java/Processing/Somoclu)Experiments to map 3d sphere centers and surface samples to a SOM and evaluate the results

This project is a Java Eclipse project using the Processing core librarires for graphics functionality and a customized version of the SOMOCLU library for the Self Organizing Map functionality.  Everything necessary to import this project into Eclipse and run it should be present.

This project is intended to provide a mechanism to gain some understanding of Self Organizing Maps.  Spheres of random, user-determinable sizes can be generated, with samples on their surfaces, and a SOM can be configured and trained on these spheres, or their samples.  The resultant weight-space of the SOM is then displayed, as well as the partitions of that space corresponding to specific spheres.

Here's some cool vids that illustrate this in use : 
First, the spheres generated, with both random colors and their loc as colors, and samples on their surface : 

https://dl.dropboxusercontent.com/u/55351229/SpheresInCube.mp4

The SOM can be trained either on the locations of the centers or the surface samples of the spheres, and can be displayed either with the values represented at a particular location as colors or with segmentations corresponding to the spheres that each section of the map represents, shown in the colors of the spheres assigned randomly on their creation : 

https://dl.dropboxusercontent.com/u/55351229/SOMandSpheres.mp4

More images of the segmentation map - note how spheres 40 and 43 on the map are adjacent, reflecting their closeness in the 3D sphere space : 

https://dl.dropboxusercontent.com/u/55351229/SOMandSpheres2.mp4

This vid is interesting - it toggles between the spheres represented at their original coordinates and the locations of the nodes on the map they most strongly map to.  Inter-sphere distances exhibit some interesting behavior - close spheres get pushed together, while those that are further apart retain their distance.  Unsupervised clustering - take that k-means - we don't need your steeeenking k!

https://dl.dropboxusercontent.com/u/55351229/vid1SpheresAndMapSpheres.mp4

Last example (for now) : here's the results of the map trained only on samples generated on the surface of the spheres (no sphere centers).  The cool thing here is that the map learned the sphere centers just from the surface locations, which is appropriate since the sphere samples were generated evenly across the surface.  The fact that all the samples got assigned to the centers is a natural consequence of the map's tendency to cluster close elements closer (via the decaying update radius, which causes distant elements on the map (measured in map coords) to not be moved closer to any updating node in each update as often as closer elements are).  The better sphere centers are probably just a consequence of having 200x more data.

https://dl.dropboxusercontent.com/u/55351229/smplsAndMapSmpls.mp4







