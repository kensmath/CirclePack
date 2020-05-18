function z = s_pt_to_plane(sz)
%z = s_pt_to_plane(sz) Stereo project spherical pt sz to plane
%   sz is complex of form (theta,phi). Recall stereographic
%   projection is from the SOUTH pole.

cosphi=cos(imag(sz));
if cosphi<-.999999999
    z=10000*(cos(real(sz))+sin(real(sz))*1i);
    return;
end

r=sin(imag(sz))/(1.0+cosphi);
z=r*(cos(real(sz))+sin(real(sz))*1i);
return;

end

