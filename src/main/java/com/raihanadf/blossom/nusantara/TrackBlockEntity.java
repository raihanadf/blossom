package com.raihanadf.blossom.nusantara;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.raihanadf.blossom.registry.BlossomBlocks;

public class TrackBlockEntity extends BlockEntity {
	public record Piece(float x, float y, float z, float yaw, float pitch, float length) {
	}

	private final List<Piece> pieces = new ArrayList<>();
	// the deck of a turntable: its pieces swing round with the loco standing on it
	private boolean turntable;

	public TrackBlockEntity(BlockPos pos, BlockState state) {
		super(BlossomBlocks.TRACK_ENTITY, pos, state);
	}

	public List<Piece> pieces() {
		return pieces;
	}

	public boolean turntable() {
		return turntable;
	}

	public void makeTurntable() {
		turntable = true;
		setChanged();
	}

	public void addPiece(float x, float y, float z, float yaw, float pitch, float length) {
		addPiece(x, y, z, yaw, pitch, length, false);
	}

	// `once`: skip a piece this block already has (laying the same rails again, as a repair does)
	public void addPiece(float x, float y, float z, float yaw, float pitch, float length, boolean once) {
		if (once) {
			for (Piece piece : pieces) {
				if (Math.abs(piece.x() - x) < 0.05f && Math.abs(piece.y() - y) < 0.05f && Math.abs(piece.z() - z) < 0.05f
						&& Math.abs(Mth.wrapDegrees(piece.yaw() - yaw)) < 1f) {
					return;
				}
			}
		}
		pieces.add(new Piece(x, y, z, yaw, pitch, Math.max(0.01f, length)));
		if (level != null) {
			setChanged();
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		pieces.clear();
		turntable = input.getBooleanOr("Turntable", false);
		for (ValueInput piece : input.childrenListOrEmpty("Pieces")) {
			pieces.add(new Piece(piece.getFloatOr("X", 0), piece.getFloatOr("Y", 0), piece.getFloatOr("Z", 0),
					piece.getFloatOr("Yaw", 0), piece.getFloatOr("Pitch", 0), Math.max(0.01f, piece.getFloatOr("Length", 1))));
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putBoolean("Turntable", turntable);
		ValueOutput.ValueOutputList list = output.childrenList("Pieces");
		for (Piece piece : pieces) {
			ValueOutput child = list.addChild();
			child.putDouble("X", piece.x());
			child.putDouble("Y", piece.y());
			child.putDouble("Z", piece.z());
			child.putDouble("Yaw", piece.yaw());
			child.putDouble("Pitch", piece.pitch());
			child.putDouble("Length", piece.length());
		}
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}
}
