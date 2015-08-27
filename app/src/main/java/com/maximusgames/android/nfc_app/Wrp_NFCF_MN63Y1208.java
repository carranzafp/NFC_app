package com.maximusgames.android.nfc_app;

import android.nfc.Tag;
import android.nfc.tech.NfcF;
//import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by maximus on 26/08/2015.
 */
//Esta es una Wrapper porque contiene una instancia de NfcF tag
public class Wrp_NFCF_MN63Y1208 {
    public static final int CMD_READ = 0x06;
    public static final int CMD_WRITE = 0x08;
    public static final int BLOCK_2BYTES =2;
    public static final int BLOCK_3BYTES =3;
    public static final int MODE_RF_PLAIN = 0x00;
    public static final int MODE_RF_ENCR_PRIVKEY = 0x02;
    public static final int MODE_RF_ENCR_FAMKEY = 0x03;
    public static final int MODE_TUNNEL_PLAIN = 0x04;
    public static final int MODE_TUNNEL_ENCR_PRIVKEY = 0x06;
    public static final int MODE_TUNNEL_ENCR_FAMKEY = 0x07;
    //private byte[] mRawBytes;

    private byte[] mstatusflag=new byte[2];
    private boolean mResultOK;
    private NfcF mNfcFTag;
    private int mLen;
    private int mCommand;
    private byte[] mPICCCode;
    private int mSvsNum;
    private byte[] mSVS=new byte[2];
    private int mNumBlocks;
    private List<LSI_Block> mBlocks=new ArrayList<LSI_Block>();

    public Wrp_NFCF_MN63Y1208(Tag tag) {
        mNfcFTag=NfcF.get(tag);
        mPICCCode = tag.getId();
        mSvsNum=0x01;//fixed
        mSVS[0]=(byte)0x90;   //Dont care according to datasheet
        mSVS[1]=(byte)0x00;   //Dont care according to datasheet
        mstatusflag[0]=0x00;
        mstatusflag[1]=0x00;
        mResultOK=false;
    }

    public NfcF getNfcFTag() {
        return mNfcFTag;
    }

    /*    public NFCF_MN63Y1208(byte[] PICCCode) {
        mPICCCode = PICCCode;
        mSvsNum=0x01;//fixed
        mSVS[0]=(byte)0x90;   //Dont care according to datasheet
        mSVS[1]=(byte)0x00;   //Dont care according to datasheet
    }*/

    public void AddBlock(int blocknum) {
        mBlocks.add(new LSI_Block(blocknum));
    }
    public void AddBlock(int blocknum,int mode) {
        mBlocks.add(new LSI_Block(blocknum,mode));
    }
    public void AddBlock(int blocknum,byte[] data) {
        mBlocks.add(new LSI_Block(blocknum,data));
    }
    public void AddBlock(int blocknum,int mode,byte[] data) {
        mBlocks.add(new LSI_Block(blocknum, mode, data));
    }
    public void RemoveBlocks() {
        mBlocks.clear();
    }

    public byte[] getBlockData(int blocknum) {
        for(LSI_Block block : mBlocks) {
            if(block.getBlockNum()==blocknum) {
                return  block.getData();
            }
        }
        //default
        return null;
    }

    //Generador de byte array
    public byte[] ReadCmd() {
        //Calculate Maximum Array size
        mLen=14; //Len itself + command code + PICC CODE + SVSNUM + SVS(2bytes) + Blocknum = 14 bytes
        //Now add the block sizes
        for(LSI_Block block : mBlocks) {
            mLen+=block.getBlocType();
        }
        //Now we hopefully have the final size
        byte[] response=new byte[mLen];
        //Now fill it
        response[0]=(byte)mLen;
        response[1]=(byte) CMD_READ;
        //copy PICC Code
        System.arraycopy(mPICCCode,0,response,2,8);

        response[10]=(byte)mSvsNum; //svs num
        System.arraycopy(mSVS,0,response,11,2); //svs

        response[13]=(byte)mBlocks.size();  //num of blocks

        int index=14;
        for(LSI_Block block : mBlocks) {
            System.arraycopy(block.blockList(),0,response,index,block.getBlocType());
            index+=block.getBlocType();
        }
        return response;
    }

    public byte[] WriteCmd() {
        //Calculate Maximum Array size
        mLen=14; //Len itself + command code + PICC CODE + SVSNUM + SVS(2bytes) + Blocknum = 14 bytes
        //Now add the block sizes
        for(LSI_Block block : mBlocks) {
            mLen+=block.getBlocType();
            mLen+=16; //Add space for the data
        }
        //Now we hopefully have the final size
        byte[] response=new byte[mLen];
        //Now fill it
        response[0]=(byte)mLen;
        response[1]=(byte) CMD_WRITE;
        //copy PICC Code
        System.arraycopy(mPICCCode,0,response,2,8);

        response[10]=(byte)mSvsNum; //svs num
        System.arraycopy(mSVS,0,response,11,2); //svs

        response[13]=(byte)mBlocks.size();  //num of blocks

        int index=14;
        for(LSI_Block block : mBlocks) {
            System.arraycopy(block.blockList(),0,response,index,block.getBlocType());
            index+=block.getBlocType();
            //Now copy the data
            System.arraycopy(block.getData(),0,response,index,16); //copy full 16 bytes
            index+=16; //increase size accordingly

        }
        return response;
    }

    public void executeRead() throws IOException{
        try {
            mNfcFTag.connect();
            byte[] response=mNfcFTag.transceive(ReadCmd());
            mNfcFTag.close();
            //Now lets parse the response
            setFlags(response);
            //If result was ok we must fill in the block data from the response

            if(mResultOK) {
                int numblocks=response[12];
                int index=13;
                //We will assume the data is returned in the same order as the read blocks were specified
                for(int i=0;i<numblocks;i++) {
                    byte[] data = new byte[16];
                    System.arraycopy(response, index, data, 0, 16);
                    mBlocks.get(i).setData(data);
                    index+=16;
                }
            }
            //return response;
        }
        catch (IOException e) {
            throw new IOException(e);
        }
    }



    public void executeWrite() throws IOException{
        try {
            mNfcFTag.connect();
            byte[] response=mNfcFTag.transceive(WriteCmd());
            mNfcFTag.close();
            //Now lets parse the response
            setFlags(response);
        }
        catch (IOException e) {
            throw new IOException(e);
        }
    }
    private void setFlags(byte [] response) {
        mResultOK=false;//default
        System.arraycopy(response,10,mstatusflag,0,2); //Copy status flags
        if(mstatusflag[0]==(byte)0x00 && mstatusflag[1]==(byte)0x00) {
            mResultOK=true;
        }
    }

    public boolean isResultOK() {
        return mResultOK;
    }

    private class LSI_Block {
        private int mBlockNum;
        private byte[] mD=new byte[3];
        private int mBlockType;
        private byte[] mData=new byte[16]; //data belonging this block (used on write commands)

        //Create constructors
        //Single method to parse 2byte block
        public LSI_Block(int blocknum) {
            mBlockNum=blocknum;
            mBlockType= BLOCK_2BYTES;
            //Form the response
            mD[0]=(byte)0x80;
            mD[1]=(byte)mBlockNum;
        }

        public LSI_Block(int blocknum,byte[] data) {
            mBlockNum=blocknum;
            mBlockType= BLOCK_2BYTES;
            //Form the response
            mD[0]=(byte)0x80;
            mD[1]=(byte)mBlockNum;
            System.arraycopy(data,0,mData,0,16);    //Copy full 16 byte block
        }

        //Method to form 3 byte blocks
        public LSI_Block(int blocknum, int mode) {
            //Crear array de retorno
            mBlockNum=blocknum;
            mBlockType= BLOCK_3BYTES;
            mD[0]=(byte)0x00;
            mD[1]=(byte)mBlockNum;
            mD[2]=(byte) (mode & 0x07);
        }

        //Method to form 3 byte blocks
        public LSI_Block(int blocknum, int mode, byte[] data) {
            //Crear array de retorno
            mBlockNum=blocknum;
            mBlockType= BLOCK_3BYTES;
            mD[0]=(byte)0x00;
            mD[1]=(byte)mBlockNum;
            mD[2]=(byte) (mode & 0x07);
            System.arraycopy(data,0,mData,0,16);    //Copy full 16 byte block
        }

        public int getBlocType() {
            return mBlockType;
        }

        public int getBlockNum() {
            return mBlockNum;
        }

        public byte[] blockList() {
            byte[] resp=new byte[mBlockType];
            System.arraycopy(mD,0,resp,0,mBlockType);
            return resp;
        }
        public byte[] getData() {
            return mData;
        }

        public void setData(byte[] data) {
            mData = data;
        }
    };

}
