package slimeknights.mantle.client.model.util;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Transformation;
import com.mojang.math.Vector3f;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.RenderTypeGroup;
import net.minecraftforge.client.model.IModelBuilder;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.LogicHelper;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static net.minecraft.client.renderer.block.model.BlockModel.FACE_BAKERY;
import static slimeknights.mantle.client.model.util.SimpleBlockModel.BAKE_LOCATION;

/**
 * Block model for setting color, luminosity, and per element uv lock. Similar to {@link MantleItemLayerModel} but
 * for blocks
 */
// TODO 1.19: extend SimpleBlockModel after cleaning up some functions in that class
@RequiredArgsConstructor
public class ColoredBlockModel implements IUnbakedGeometry<ColoredBlockModel> {

  @Getter
  private final SimpleBlockModel model;
  /**
   * Colors to use for each piece
   */
  @Getter
  private final List<ColorData> colorData;



  /**
   * Bakes a single part of the model into the builder
   *
   * @param builder      Baked model builder
   * @param owner        Model owner
   * @param part         Part to bake
   * @param color        Color tint, use -1 for no tint
   * @param luminosity   Luminosity for fullbright, use 0 for normal lighting
   * @param transform    Transform for the face
   * @param spriteGetter Sprite getter
   * @param location     Model location
   */
  public static void bakePart(IModelBuilder builder, IGeometryBakingContext owner, BlockElement part,
                              int color, int luminosity, ModelState transform,
                              Function<Material, TextureAtlasSprite> spriteGetter, ResourceLocation location) {
    bakePart(builder, owner, part, color, luminosity, transform.getRotation(), transform.isUvLocked(), spriteGetter,
      location);
  }

  /**
   * Bakes a single part of the model into the builder
   * @param builder       Baked model builder
   * @param owner         Model owner
   * @param part          Part to bake
   * @param color         Color tint, use -1 for no tint
   * @param luminosity    Luminosity for fullbright, use 0 for normal lighting
   * @param transform     Transform for the face
   * @param uvlock        UV lock for the face, separated to allow overriding the model state
   * @param spriteGetter  Sprite getter
   * @param location      Model location
   */
  public static void bakePart(IModelBuilder builder, IGeometryBakingContext owner, BlockElement part, int color,
                              int luminosity, Transformation transform, boolean uvlock, Function<Material,
    TextureAtlasSprite> spriteGetter, ResourceLocation location) {
    for (Direction direction : part.faces.keySet()) {
      BlockElementFace face = part.faces.get(direction);
      // ensure the name is not prefixed (it always is)
      String texture = face.texture;
      if (texture.charAt(0) == '#') {
        texture = texture.substring(1);
      }
      // bake the face with the extra colors
      TextureAtlasSprite sprite = spriteGetter.apply(owner.getMaterial(texture));
      BakedQuad quad = bakeFace(part, face, sprite, direction, transform, uvlock, color, luminosity, location);
      // apply cull face
      if (face.cullForDirection == null) {
        builder.addUnculledFace(quad);
      } else {
        builder.addCulledFace(Direction.rotate(transform.getMatrix(), face.cullForDirection), quad);
      }
    }
  }

  /**
   * Bakes a list of block part elements into a model
   *
   * @param context      Model configuration
   * @param elements     Model elements
   * @param transform    Model transform
   * @param overrides    Model overrides
   * @param spriteGetter Sprite getter instance
   * @param location     Model bake location
   * @return Baked model
   */
  public static BakedModel bakeModel(IGeometryBakingContext context, List<BlockElement> elements,
                                     List<ColorData> colorData, ModelState transform, ItemOverrides overrides,
                                     Function<Material, TextureAtlasSprite> spriteGetter, ResourceLocation location) {
    // iterate parts, adding to the builder
    TextureAtlasSprite particle = spriteGetter.apply(context.getMaterial("particle"));
    var renderTypeHint = context.getRenderTypeHint();
    var renderTypes = renderTypeHint != null ? context.getRenderType(renderTypeHint) : RenderTypeGroup.EMPTY;
    IModelBuilder<?> builder = IModelBuilder.of(context.useAmbientOcclusion(), context.useBlockLight(),
      context.isGui3d(), context.getTransforms(), overrides, particle, renderTypes);
    int size = elements.size();
    Transformation transformation = transform.getRotation();
    boolean uvlock = transform.isUvLocked();
    for (int i = 0; i < size; i++) {
      BlockElement part = elements.get(i);
      ColorData colors = LogicHelper.getOrDefault(colorData, i, ColorData.DEFAULT);
      bakePart(builder, context, part, colors.color, colors.luminosity(), transformation, colors.isUvLock(uvlock), spriteGetter, location);
    }
    return builder.build();
  }

  @Override
  public BakedModel bake(IGeometryBakingContext context, ModelBakery bakery,
                         Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
    return model.bake(context,bakery,spriteGetter,modelState,overrides,modelLocation);
  }

  public BakedModel bakeDynamic(IGeometryBakingContext owner, ModelState transform) {
    return bakeModel(owner, model.getElements(), colorData, transform, ItemOverrides.EMPTY,Material::sprite,
      BAKE_LOCATION);
  }

  @Override
  public Collection<Material> getMaterials(IGeometryBakingContext context, Function<ResourceLocation, UnbakedModel> modelGetter, Set<Pair<String, String>> missingTextureErrors) {
    return model.getMaterials(context,modelGetter,missingTextureErrors);
  }

  /**
   * Data class for setting properties when baking colored elements
   */
  public record ColorData(int color, int luminosity, @Nullable Boolean uvlock) {
    public static final ColorData DEFAULT = new ColorData(-1, 0, null);

    /** @deprecated use {@link #ColorData(int, int, Boolean)} */
    @Deprecated
    public ColorData(int color, int luminosity) {
      this(color, luminosity, null);
    }

    /** Gets the UV lock for the given part */
    public boolean isUvLock(boolean defaultLock) {
      if (uvlock == null) {
        return defaultLock;
      }
      return uvlock;
    }

    /**
     * Parses the color data from JSON
     */
    public static ColorData fromJson(JsonObject json) {
      int color = JsonHelper.parseColor(GsonHelper.getAsString(json, "color", ""));
      int luminosity = GsonHelper.getAsInt(json, "luminosity", 0);
      Boolean uvlock = null;
      if (json.has("uvlock")) {
        uvlock = GsonHelper.getAsBoolean(json, "uvlock");
      }
      return new ColorData(color, luminosity, uvlock);
    }
  }


  /* Deserializing */

  /** Deserializes the model from JSON */
  public static ColoredBlockModel deserialize(JsonDeserializationContext context, JsonObject json) {
    SimpleBlockModel model = SimpleBlockModel.deserialize(context, json);
    List<ColorData> colorData = json.has("colors") ? JsonHelper.parseList(json, "colors", ColorData::fromJson) : Collections.emptyList();
    return new ColoredBlockModel(model,colorData);
  }

  /* Face bakery */

  /** @deprecated use {@link #bakeFace(BlockElement, BlockElementFace, TextureAtlasSprite, Direction, Transformation, boolean, int, int, ResourceLocation)} */
  @Deprecated
  public static BakedQuad bakeFace(BlockElement part, BlockElementFace face, TextureAtlasSprite sprite, Direction facing, ModelState transform, int color, int luminosity, ResourceLocation location) {
    return bakeFace(part, face, sprite, facing, transform.getRotation(), transform.isUvLocked(), color, luminosity, location);
  }



  /**
   * Extension of {@code BlockModel#bakeFace(BlockPart, BlockPartFace, TextureAtlasSprite, Direction, IModelTransform, ResourceLocation)} with color and luminosity arguments
   * @param part        Part containing the face
   * @param face        Face data
   * @param sprite      Sprite for the face
   * @param facing      Direction of the face
   * @param transform   Transform for the face
   * @param uvlock      UV lock for the face, separated to allow overriding the model state
   * @param color       Hard tint for the part in AARRGGBB format, use -1 for no tint
   * @param luminosity  Lighting for the part, 0 for no extra lighting
   * @param location    Model location for errors
   */
  public static BakedQuad bakeFace(BlockElement part, BlockElementFace face, TextureAtlasSprite sprite, Direction facing, Transformation transform, boolean uvlock, int color, int luminosity, ResourceLocation location) {
    return bakeQuad(part.from, part.to, face, sprite, facing, transform, uvlock, part.rotation, part.shade, color, luminosity, location);
  }

  /** @deprecated use {@link #bakeQuad(Vector3f, Vector3f, BlockElementFace, TextureAtlasSprite, Direction, Transformation, boolean, BlockElementRotation, boolean, int, int, ResourceLocation)} */
  @Deprecated
  public static BakedQuad bakeQuad(Vector3f posFrom, Vector3f posTo, BlockElementFace face, TextureAtlasSprite sprite,
                                   Direction facing, ModelState transform, @Nullable BlockElementRotation partRotation,
                                   boolean shade, int color, int luminosity, ResourceLocation location) {
    return bakeQuad(posFrom, posTo, face, sprite, facing, transform.getRotation(), transform.isUvLocked(), partRotation, shade, color, luminosity, location);
  }

  /**
   * Extension of {@link FaceBakery#bakeQuad(Vector3f, Vector3f, BlockElementFace, TextureAtlasSprite, Direction, ModelState, BlockElementRotation, boolean, ResourceLocation)} with color and luminosity arguments
   * @param posFrom        Face start position
   * @param posTo          Face end position
   * @param face           Face data
   * @param sprite         Sprite for the face
   * @param facing         Direction of the face
   * @param transform      Transform for the face
   * @param uvlock         UV lock for the face, separated to allow overriding the model state
   * @param partRotation   Rotation for the part
   * @param shade          If true, shades the part
   * @param color          Hard tint for the part in AARRGGBB format, use -1 for no tint
   * @param luminosity     Lighting for the part, 0 for no extra lighting
   * @param location       Model location for errors
   * @return  Baked quad
   */
  public static BakedQuad bakeQuad(Vector3f posFrom, Vector3f posTo, BlockElementFace face, TextureAtlasSprite sprite,
                                   Direction facing, Transformation transform, boolean uvlock, @Nullable BlockElementRotation partRotation,
                                   boolean shade, int color, int luminosity, ResourceLocation location) {
    BlockFaceUV faceUV = face.uv;
    if (uvlock) {
      faceUV = FaceBakery.recomputeUVs(face.uv, facing, transform, location);
    }

    float[] originalUV = new float[faceUV.uvs.length];
    System.arraycopy(faceUV.uvs, 0, originalUV, 0, originalUV.length);
    float shrinkRatio = sprite.uvShrinkRatio();
    float u = (faceUV.uvs[0] + faceUV.uvs[0] + faceUV.uvs[2] + faceUV.uvs[2]) / 4.0F;
    float v = (faceUV.uvs[1] + faceUV.uvs[1] + faceUV.uvs[3] + faceUV.uvs[3]) / 4.0F;
    faceUV.uvs[0] = Mth.lerp(shrinkRatio, faceUV.uvs[0], u);
    faceUV.uvs[2] = Mth.lerp(shrinkRatio, faceUV.uvs[2], u);
    faceUV.uvs[1] = Mth.lerp(shrinkRatio, faceUV.uvs[1], v);
    faceUV.uvs[3] = Mth.lerp(shrinkRatio, faceUV.uvs[3], v);

    int[] vertexData = makeVertices(faceUV, sprite, facing, FACE_BAKERY.setupShape(posFrom, posTo), transform, partRotation, color, luminosity);
    Direction direction = FaceBakery.calculateFacing(vertexData);
    System.arraycopy(originalUV, 0, faceUV.uvs, 0, originalUV.length);
    if (partRotation == null) {
      FACE_BAKERY.recalculateWinding(vertexData, direction);
    }
    ForgeHooksClient.fillNormal(vertexData, direction);
    return new BakedQuad(vertexData, face.tintIndex, direction, sprite, shade);
  }

  /** Clone of the vanilla method with 2 extra parameters */
  private static int[] makeVertices(BlockFaceUV uvs, TextureAtlasSprite sprite, Direction orientation, float[] posDiv16, Transformation rotationIn, @Nullable BlockElementRotation partRotation, int color, int luminosity) {
    int[] vertexData = new int[32];
    for(int i = 0; i < 4; ++i) {
      bakeVertex(vertexData, i, orientation, uvs, posDiv16, sprite, rotationIn, partRotation, color, luminosity);
    }
    return vertexData;
  }

  /** Clone of the vanilla method with 2 extra parameters */
  private static void bakeVertex(int[] vertexData, int vertexIndex, Direction facing, BlockFaceUV blockFaceUVIn, float[] posDiv16, TextureAtlasSprite sprite, Transformation rotationIn, @Nullable BlockElementRotation partRotation, int color, int luminosity) {
    FaceInfo.VertexInfo vertexInfo = FaceInfo.fromFacing(facing).getVertexInfo(vertexIndex);
    Vector3f vector3f = new Vector3f(posDiv16[vertexInfo.xFace], posDiv16[vertexInfo.yFace], posDiv16[vertexInfo.zFace]);
    FACE_BAKERY.applyElementRotation(vector3f, partRotation);
    FACE_BAKERY.applyModelRotation(vector3f, rotationIn);
    fillVertex(vertexData, vertexIndex, vector3f, sprite, blockFaceUVIn, color, luminosity);
  }

  /**
   * Converts an ARGB color to an ABGR color, as the commonly used color format is not the format colors end up packed into.
   * This function doubles as its own inverse, not that its needed.
   * @param color  ARGB color
   * @return  ABGR color
   */
  private static int swapColorRedBlue(int color) {
    return (color & 0xFF00FF00) // alpha and green same spot
           | ((color >> 16) & 0x000000FF) // red moves to blue
           | ((color << 16) & 0x00FF0000); // blue moves to red
  }

  /** Clone of the vanilla method with 2 extra parameters, major logic changes are in this code */
  private static void fillVertex(int[] vertexData, int vertexIndex, Vector3f vector, TextureAtlasSprite sprite, BlockFaceUV blockFaceUV, int color, int luminosity) {
    int i = vertexIndex * 8;
    // XYZ - 3 ints
    vertexData[i] = Float.floatToRawIntBits(vector.x());
    vertexData[i + 1] = Float.floatToRawIntBits(vector.y());
    vertexData[i + 2] = Float.floatToRawIntBits(vector.z());
    // color - 1 int in ABGR format, we use ARGB format as that is used everywhere else. vanilla uses -1 here
    vertexData[i + 3] = swapColorRedBlue(color);
    // UV - 2 ints
    vertexData[i + 4] = Float.floatToRawIntBits(sprite.getU((double)blockFaceUV.getU(vertexIndex) * .999 + blockFaceUV.getU((vertexIndex + 2) % 4) * .001));
    vertexData[i + 5] = Float.floatToRawIntBits(sprite.getV((double)blockFaceUV.getV(vertexIndex) * .999 + blockFaceUV.getV((vertexIndex + 2) % 4) * .001));
    // light UV - 1 ints, just setting block light here rather than block and sky. vanilla uses 0 here
    vertexData[i + 6] = (luminosity << 4);
  }
}
