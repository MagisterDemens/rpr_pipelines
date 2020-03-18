
def call(String fileName, String linkText, String link)
{
    String str = "<a href=\"${link}\">${linkText}</a><br>"
    bat"""
        echo ^<a href=\"${link}\"^>${linkText}^</a^>^<br^> >> ${fileName}
    """
 /*   
    new File('/Users/me/Downloads', 'myImage.gif').withOutputStream { os ->
        os << str
    }

    def f = new File(fileName)
    if(f.exists())
    {
        f.append(str)
    }
    else
    {
        f.write(str)
    }
   */
}
