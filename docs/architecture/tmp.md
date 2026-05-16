(Dont Forget about CHECKING MY ENGLISH)
It seems that FrameBackground should be similar to AlbumBackground except anchorMode. 
Also I want to do it more structural - what do you think?
(
introduce family of Classes - 
SolidColorBackgroundData:BackgroundData {
    val color: String = "#000000"
}

TextureBackgroundData:BackgroundData {
    val textureRefId: String,
    val tileData: TileData
}

ProceduralBackgroundData:BackgroundData {
    val procedural: ProceduralPattern? = null,
    val tileData: TileData
}

TileData: {
    val tileMode: TileMode = TileMode.None,
    val tileOriginX: Float = 0f,
    val tileOriginY: Float = 0f,
    val tileWidth: Float = 200f,
    val tileHeight: Float = 200f,
}

) 
