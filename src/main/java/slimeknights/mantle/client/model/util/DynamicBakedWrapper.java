package slimeknights.mantle.client.model.util;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;

import java.util.Random;

/**
 * Cross between {@link BakedModelWrapper} and {@link net.minecraftforge.client.model.IDynamicBakedModel}.
 * Used to create a baked model wrapper that has a dynamic {@link #getQuads(BlockState, Direction, RandomSource)} without worrying about overriding the 
 * deprecated variant.
 * @param <T>  Baked model parent
 */
public abstract class DynamicBakedWrapper<T extends BakedModel> extends BakedModelWrapper<T> {

  protected DynamicBakedWrapper(T originalModel) {
    super(originalModel);
  }

}
