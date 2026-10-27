// BNEL Windows recycle adapter. No permanent-delete path is provided.
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <shellapi.h>
#include <shlobj.h>
#include <wrl.h>
#include <jni.h>
#include <string>

using Microsoft::WRL::ComPtr;
using Microsoft::WRL::RuntimeClass;
using Microsoft::WRL::RuntimeClassFlags;
using Microsoft::WRL::ClassicCom;

class RecycleGuard final : public RuntimeClass<RuntimeClassFlags<ClassicCom>,IFileOperationProgressSink> {
public:
    std::wstring recycled;
    HRESULT STDMETHODCALLTYPE PreDeleteItem(DWORD flags,IShellItem*) override {
        // The Shell removes this flag when an item cannot be recycled. Abort then.
        return (flags & TSF_DELETE_RECYCLE_IF_POSSIBLE) ? S_OK : E_ABORT;
    }
    HRESULT STDMETHODCALLTYPE PostDeleteItem(DWORD,IShellItem*,HRESULT result,IShellItem* created) override {
        if(FAILED(result)||!created)return E_ABORT;
        PWSTR path=nullptr;
        HRESULT hr=created->GetDisplayName(SIGDN_FILESYSPATH,&path);
        if(SUCCEEDED(hr)&&path){recycled=path;CoTaskMemFree(path);}
        return hr;
    }
    HRESULT STDMETHODCALLTYPE StartOperations() override{return S_OK;}
    HRESULT STDMETHODCALLTYPE FinishOperations(HRESULT) override{return S_OK;}
    HRESULT STDMETHODCALLTYPE UpdateProgress(UINT,UINT) override{return S_OK;}
    HRESULT STDMETHODCALLTYPE ResetTimer() override{return S_OK;}
    HRESULT STDMETHODCALLTYPE PauseTimer() override{return S_OK;}
    HRESULT STDMETHODCALLTYPE ResumeTimer() override{return S_OK;}
    HRESULT STDMETHODCALLTYPE PreRenameItem(DWORD,IShellItem*,LPCWSTR) override{return E_ABORT;}
    HRESULT STDMETHODCALLTYPE PostRenameItem(DWORD,IShellItem*,LPCWSTR,HRESULT,IShellItem*) override{return E_ABORT;}
    HRESULT STDMETHODCALLTYPE PreMoveItem(DWORD,IShellItem*,IShellItem*,LPCWSTR) override{return E_ABORT;}
    HRESULT STDMETHODCALLTYPE PostMoveItem(DWORD,IShellItem*,IShellItem*,LPCWSTR,HRESULT,IShellItem*) override{return E_ABORT;}
    HRESULT STDMETHODCALLTYPE PreCopyItem(DWORD,IShellItem*,IShellItem*,LPCWSTR) override{return E_ABORT;}
    HRESULT STDMETHODCALLTYPE PostCopyItem(DWORD,IShellItem*,IShellItem*,LPCWSTR,HRESULT,IShellItem*) override{return E_ABORT;}
    HRESULT STDMETHODCALLTYPE PreNewItem(DWORD,IShellItem*,LPCWSTR) override{return E_ABORT;}
    HRESULT STDMETHODCALLTYPE PostNewItem(DWORD,IShellItem*,LPCWSTR,LPCWSTR,DWORD,HRESULT,IShellItem*) override{return E_ABORT;}
};

static bool localRegularFile(const std::wstring& path){
    // Disallow UNC, device paths, removable/network drives and reparse points.
    if(path.size()<4||path[1]!=L':'||path[2]!=L'\\'||path.size()>32760)return false;
    wchar_t volume[MAX_PATH]{};
    if(!GetVolumePathNameW(path.c_str(),volume,MAX_PATH)||GetDriveTypeW(volume)!=DRIVE_FIXED)return false;
    DWORD attrs=GetFileAttributesW(path.c_str());
    if(attrs==INVALID_FILE_ATTRIBUTES||(attrs&(FILE_ATTRIBUTE_DIRECTORY|FILE_ATTRIBUTE_REPARSE_POINT)))return false;
    std::wstring parent=path;
    while(parent.size()>3){
        parent=parent.substr(0,parent.find_last_of(L'\\'));
        if(parent.size()<3)break;
        attrs=GetFileAttributesW(parent.c_str());
        if(attrs==INVALID_FILE_ATTRIBUTES||(attrs&FILE_ATTRIBUTE_REPARSE_POINT))return false;
    }
    return true;
}

extern "C" JNIEXPORT jstring JNICALL Java_org_bnel_dicom_NativeRecycle_move(JNIEnv* env,jclass,jstring input){
    if(!input)return nullptr;
    const jchar* chars=env->GetStringChars(input,nullptr);if(!chars)return nullptr;
    std::wstring path(reinterpret_cast<const wchar_t*>(chars),env->GetStringLength(input));
    env->ReleaseStringChars(input,chars);
    if(!localRegularFile(path))return nullptr;
    HRESULT initialized=CoInitializeEx(nullptr,COINIT_APARTMENTTHREADED|COINIT_DISABLE_OLE1DDE);
    if(FAILED(initialized))return nullptr;
    std::wstring recycled;
    {
        ComPtr<IFileOperation> operation;ComPtr<IShellItem> item;
        auto guard=Microsoft::WRL::Make<RecycleGuard>();
        HRESULT hr=CoCreateInstance(CLSID_FileOperation,nullptr,CLSCTX_INPROC_SERVER,IID_PPV_ARGS(&operation));
        if(SUCCEEDED(hr))hr=operation->SetOperationFlags(FOFX_RECYCLEONDELETE|FOFX_ADDUNDORECORD|FOFX_EARLYFAILURE|FOF_NO_UI|FOF_NO_CONNECTED_ELEMENTS);
        if(SUCCEEDED(hr))hr=SHCreateItemFromParsingName(path.c_str(),nullptr,IID_PPV_ARGS(&item));
        if(SUCCEEDED(hr))hr=operation->DeleteItem(item.Get(),guard.Get());
        if(SUCCEEDED(hr))hr=operation->PerformOperations();
        BOOL aborted=TRUE;if(SUCCEEDED(hr))hr=operation->GetAnyOperationsAborted(&aborted);
        if(SUCCEEDED(hr)&&!aborted)recycled=guard->recycled;
    }
    CoUninitialize();
    return recycled.empty()?nullptr:env->NewString(reinterpret_cast<const jchar*>(recycled.data()),static_cast<jsize>(recycled.size()));
}
