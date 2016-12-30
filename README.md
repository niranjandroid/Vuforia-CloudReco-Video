#Vuforia-CloudReco-Video
##Aim - Detect Image and play video accordingly

This project allows you to detect predefined images and play video based on the targeted image

##Setup
1. Create a project in vuforial developer portal
  * [Register](https://developer.vuforia.com/user/register) into vuforia developer portal.
  * Go to [license manager](https://developer.vuforia.com/targetmanager/licenseManager/licenseListing) and add a license key.
2. Copy the generated license key and assign it to VUFORIA_LICENSE_KEY field in Constants.java class
3. Go to Target Manager in the portal and add a database (select "cloud" from the given options)
4. Click on the added database and you can see an option to add target image.
5. Click on "Add Target", then you can see options like "Target Image File" and "Metadata Package:(Optional)"
6. Upload an image in "Target Image File" option.
7. Upload a text file in "Metadata Package:(Optional)"
  * In text file, content should be a json data. You can give your video url and title of the image/video as well.
  * EX : {"url" : "https://test.com/path.mp4", "title" : "VideoTitle"}
  * Note: Video url should consist extension like mp4, thats why current project not supporting youtube videos
8. Install app and scan the uploaded image, you can see the defined video.
9. Change VUFORIA_SDK_DIR in build.gradle file to vuforia sdk directory in your machines local vuforia sdk storage
